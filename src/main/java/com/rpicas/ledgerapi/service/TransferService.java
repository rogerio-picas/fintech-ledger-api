package com.rpicas.ledgerapi.service;

import com.rpicas.ledgerapi.domain.Account;
import com.rpicas.ledgerapi.domain.AuditEventType;
import com.rpicas.ledgerapi.domain.AuditLog;
import com.rpicas.ledgerapi.domain.IdempotencyRecord;
import com.rpicas.ledgerapi.domain.Money;
import com.rpicas.ledgerapi.domain.Transaction;
import com.rpicas.ledgerapi.domain.TransactionStatus;
import com.rpicas.ledgerapi.exception.AccountNotFoundException;
import com.rpicas.ledgerapi.exception.BadRequestException;
import com.rpicas.ledgerapi.exception.BusinessRuleException;
import com.rpicas.ledgerapi.exception.ConflictException;
import com.rpicas.ledgerapi.exception.InsufficientFundsException;
import com.rpicas.ledgerapi.exception.TransactionNotFoundException;
import com.rpicas.ledgerapi.repository.AccountRepository;
import com.rpicas.ledgerapi.repository.AuditLogRepository;
import com.rpicas.ledgerapi.repository.IdempotencyRecordRepository;
import com.rpicas.ledgerapi.repository.TransactionRepository;
import com.rpicas.ledgerapi.web.dto.ApiError;
import com.rpicas.ledgerapi.web.dto.TransferRequest;
import com.rpicas.ledgerapi.web.dto.TransferResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 255;
    private static final String TRANSFERS_PATH = "/api/transfers";
    /**
     * 422; resolved by code to avoid depending on the UNPROCESSABLE_ENTITY/_CONTENT
     * constant name.
     */
    private static final HttpStatus UNPROCESSABLE = HttpStatus.valueOf(422);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AuditLogRepository auditLogRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final JsonMapper jsonMapper;

    public TransferService(AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            AuditLogRepository auditLogRepository,
            IdempotencyRecordRepository idempotencyRepository,
            JsonMapper jsonMapper) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.auditLogRepository = auditLogRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Moves {@code amount} from source to target atomically and idempotently.
     * <p>
     * {@link InsufficientFundsException} does <b>not</b> roll back: the FAILED
     * transaction, its
     * audit entry and the cached 422 idempotency response are committed before the
     * exception
     * reaches the {@code GlobalExceptionHandler}. Every other exception rolls back
     * everything,
     * including the idempotency claim, so the client may safely retry with the same
     * key.
     */
    @Transactional(noRollbackFor = InsufficientFundsException.class)
    public IdempotentResponse transfer(String idempotencyKey, TransferRequest request) {
        validateIdempotencyKey(idempotencyKey);
        if (request.sourceAccountId().equals(request.targetAccountId())) {
            throw new BusinessRuleException("Source and target accounts must be different");
        }
        String requestHash = RequestHasher.hash(request);

        // 1. Idempotency fast path: already processed and committed -> replay stored
        // response.
        Optional<IdempotentResponse> cached = findCompletedResponse(idempotencyKey, requestHash);
        if (cached.isPresent()) {
            return cached.get();
        }

        // 2. Claim the key. A concurrent request with the same key blocks here until
        // the
        // first one commits, then gets 0 and replays the committed response.
        if (idempotencyRepository.tryClaim(idempotencyKey, requestHash) == 0) {
            return findCompletedResponse(idempotencyKey, requestHash)
                    .orElseThrow(() -> new ConflictException(
                            "A request with Idempotency-Key '" + idempotencyKey + "' is already in progress"));
        }
        IdempotencyRecord idempotencyRecord = idempotencyRepository.findById(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Claimed idempotency record vanished: " + idempotencyKey));

        // 3. Lock both accounts in deterministic (UUID) order to rule out deadlocks.
        BigDecimal amount = Money.normalize(request.amount());
        LockedAccounts locked = lockInOrder(request.sourceAccountId(), request.targetAccountId());
        Account source = locked.get(request.sourceAccountId());
        Account target = locked.get(request.targetAccountId());

        // 4. Balance check with BigDecimal.compareTo (never equals(), which is
        // scale-sensitive).
        if (source.getBalance().compareTo(amount) < 0) {
            Transaction failed = transactionRepository.saveAndFlush(
                    Transaction.failed(source.getId(), target.getId(), amount));
            auditLogRepository.save(AuditLog.record(failed.getId(), source.getId(),
                    AuditEventType.TRANSFER_FAILED, source.getBalance(), source.getBalance()));

            InsufficientFundsException ex = new InsufficientFundsException(source.getId(), amount);
            ApiError error = ApiError.of(UNPROCESSABLE, ex.getMessage(), TRANSFERS_PATH);
            idempotencyRecord.complete(UNPROCESSABLE.value(), jsonMapper.writeValueAsString(error));
            log.info("Transfer {} failed: insufficient funds in {}", failed.getId(), source.getId());
            throw ex;
        }

        // 5. Move the money and persist transaction + audit trail + idempotency
        // response atomically.
        BigDecimal sourcePrevious = source.getBalance();
        BigDecimal targetPrevious = target.getBalance();
        source.debit(amount);
        target.credit(amount);

        Transaction tx = transactionRepository.saveAndFlush(
                Transaction.completed(source.getId(), target.getId(), amount));
        auditLogRepository.save(AuditLog.record(tx.getId(), source.getId(),
                AuditEventType.DEBIT, sourcePrevious, source.getBalance()));
        auditLogRepository.save(AuditLog.record(tx.getId(), target.getId(),
                AuditEventType.CREDIT, targetPrevious, target.getBalance()));

        String payload = jsonMapper.writeValueAsString(TransferResponse.from(tx));
        idempotencyRecord.complete(HttpStatus.CREATED.value(), payload);
        log.info("Transfer {} completed: {} -> {} amount {}", tx.getId(), source.getId(), target.getId(), amount);
        return new IdempotentResponse(HttpStatus.CREATED.value(), payload, false);
    }

    /**
     * Reverses a COMPLETED transfer by booking an inverse (compensating)
     * transaction.
     * Existing audit records are never touched; two new REVERSAL_* entries are
     * appended.
     * The original transaction is marked REVERSED.
     */
    @Transactional
    public TransferResponse reverse(UUID transactionId) {
        // Lock the original first so concurrent reversals of the same transaction
        // serialise.
        Transaction original = transactionRepository.findByIdForUpdate(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        if (original.isReversal()) {
            throw new BusinessRuleException(
                    "Transaction " + transactionId + " is itself a reversal and cannot be reversed");
        }
        if (original.getStatus() == TransactionStatus.REVERSED) {
            throw new ConflictException("Transaction " + transactionId + " has already been reversed");
        }
        if (original.getStatus() == TransactionStatus.FAILED) {
            throw new BusinessRuleException(
                    "Transaction " + transactionId + " failed and moved no funds; nothing to reverse");
        }

        LockedAccounts locked = lockInOrder(original.getSourceAccountId(), original.getTargetAccountId());
        // Funds flow back: original target is debited, original source is credited.
        Account debitAccount = locked.get(original.getTargetAccountId());
        Account creditAccount = locked.get(original.getSourceAccountId());
        BigDecimal amount = original.getAmount();

        if (debitAccount.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(debitAccount.getId(), amount);
        }

        BigDecimal debitPrevious = debitAccount.getBalance();
        BigDecimal creditPrevious = creditAccount.getBalance();
        debitAccount.debit(amount);
        creditAccount.credit(amount);

        original.markReversed();
        Transaction reversal = transactionRepository.saveAndFlush(Transaction.reversalOf(original));
        auditLogRepository.save(AuditLog.record(reversal.getId(), debitAccount.getId(),
                AuditEventType.REVERSAL_DEBIT, debitPrevious, debitAccount.getBalance()));
        auditLogRepository.save(AuditLog.record(reversal.getId(), creditAccount.getId(),
                AuditEventType.REVERSAL_CREDIT, creditPrevious, creditAccount.getBalance()));

        log.info("Transaction {} reversed by {}", original.getId(), reversal.getId());
        return TransferResponse.from(reversal);
    }

    @Transactional(readOnly = true)
    public List<TransferResponse> listAll() {
        return transactionRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(TransferResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TransferResponse get(UUID transactionId) {
        return transactionRepository.findById(transactionId)
                .map(TransferResponse::from)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }

    private Optional<IdempotentResponse> findCompletedResponse(String key, String requestHash) {
        return idempotencyRepository.findById(key)
                .filter(rec -> rec.isCompleted())
                .map(rec -> {
                    if (!rec.matches(requestHash)) {
                        // IETF draft-ietf-httpapi-idempotency-key-header: key reuse with a different
                        // payload -> 422.
                        throw new BusinessRuleException(
                                "Idempotency-Key '" + key + "' was already used with a different request payload");
                    }
                    return new IdempotentResponse(rec.getStatusCode(), rec.getResponsePayload(), true);
                });
    }

    private LockedAccounts lockInOrder(UUID a, UUID b) {
        UUID first = a.compareTo(b) <= 0 ? a : b;
        UUID second = first.equals(a) ? b : a;
        Account firstAccount = accountRepository.findByIdForUpdate(first)
                .orElseThrow(() -> new AccountNotFoundException(first));
        Account secondAccount = accountRepository.findByIdForUpdate(second)
                .orElseThrow(() -> new AccountNotFoundException(second));
        return new LockedAccounts(firstAccount, secondAccount);
    }

    private static void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BadRequestException("Idempotency-Key header must not be blank");
        }
        if (key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BadRequestException("Idempotency-Key header must be at most "
                    + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
    }

    private record LockedAccounts(Account first, Account second) {
        Account get(UUID id) {
            if (first.getId().equals(id)) {
                return first;
            }
            if (second.getId().equals(id)) {
                return second;
            }
            throw new IllegalArgumentException("Account " + id + " is not locked in this transaction");
        }
    }
}
