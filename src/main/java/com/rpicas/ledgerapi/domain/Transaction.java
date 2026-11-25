package com.rpicas.ledgerapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_account_id", nullable = false, updatable = false)
    private UUID sourceAccountId;

    @Column(name = "target_account_id", nullable = false, updatable = false)
    private UUID targetAccountId;

    @Column(name = "amount", nullable = false, updatable = false, precision = Money.PRECISION, scale = Money.SCALE)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TransactionStatus status;

    /** When this transaction is a reversal, the id of the transaction it reverses. */
    @Column(name = "reversal_of_id", updatable = false)
    private UUID reversalOfId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Transaction() {
        // for JPA
    }

    private Transaction(UUID sourceAccountId, UUID targetAccountId, BigDecimal amount,
                        TransactionStatus status, UUID reversalOfId) {
        this.sourceAccountId = sourceAccountId;
        this.targetAccountId = targetAccountId;
        this.amount = Money.normalize(amount);
        this.status = status;
        this.reversalOfId = reversalOfId;
        this.createdAt = Instant.now();
    }

    public static Transaction completed(UUID sourceAccountId, UUID targetAccountId, BigDecimal amount) {
        return new Transaction(sourceAccountId, targetAccountId, amount, TransactionStatus.COMPLETED, null);
    }

    public static Transaction failed(UUID sourceAccountId, UUID targetAccountId, BigDecimal amount) {
        return new Transaction(sourceAccountId, targetAccountId, amount, TransactionStatus.FAILED, null);
    }

    /** Builds the inverse (compensating) transaction of {@code original}. */
    public static Transaction reversalOf(Transaction original) {
        return new Transaction(original.targetAccountId, original.sourceAccountId, original.amount,
                TransactionStatus.COMPLETED, original.id);
    }

    /** The only permitted state change: COMPLETED -> REVERSED. */
    public void markReversed() {
        if (status != TransactionStatus.COMPLETED) {
            throw new IllegalStateException("Only COMPLETED transactions can be reversed, was " + status);
        }
        this.status = TransactionStatus.REVERSED;
    }

    public boolean isReversal() {
        return reversalOfId != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public UUID getTargetAccountId() {
        return targetAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public UUID getReversalOfId() {
        return reversalOfId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
