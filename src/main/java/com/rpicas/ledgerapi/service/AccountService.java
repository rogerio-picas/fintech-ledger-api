package com.rpicas.ledgerapi.service;

import com.rpicas.ledgerapi.domain.Account;
import com.rpicas.ledgerapi.exception.AccountNotFoundException;
import com.rpicas.ledgerapi.exception.ConflictException;
import com.rpicas.ledgerapi.repository.AccountRepository;
import com.rpicas.ledgerapi.repository.AuditLogRepository;
import com.rpicas.ledgerapi.web.dto.AccountResponse;
import com.rpicas.ledgerapi.web.dto.AuditLogResponse;
import com.rpicas.ledgerapi.web.dto.CreateAccountRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final AuditLogRepository auditLogRepository;

    public AccountService(AccountRepository accountRepository, AuditLogRepository auditLogRepository) {
        this.accountRepository = accountRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public AccountResponse create(CreateAccountRequest request) {
        if (accountRepository.existsByIban(request.iban())) {
            throw new ConflictException("An account with IBAN " + request.iban() + " already exists");
        }
        // A concurrent insert of the same IBAN is still caught by the unique constraint
        // (DataIntegrityViolationException -> 409 in GlobalExceptionHandler).
        Account account = accountRepository.save(
                new Account(request.ownerName().trim(), request.iban(), request.initialBalance()));
        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listAll() {
        return accountRepository.findAllByOrderByCreatedAtAsc().stream()
                .map(AccountResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse get(UUID id) {
        return accountRepository.findById(id)
                .map(AccountResponse::from)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getAuditTrail(UUID accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        return auditLogRepository.findByAccountIdOrderByIdAsc(accountId).stream()
                .map(AuditLogResponse::from)
                .toList();
    }
}
