package com.rpicas.ledgerapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit trail of balance movements.
 * <ul>
 *   <li>No setters: state is fixed at construction.</li>
 *   <li>{@link Immutable}: Hibernate never issues UPDATEs for this entity.</li>
 *   <li>All columns are {@code updatable = false}.</li>
 *   <li>A database trigger rejects UPDATE/DELETE on {@code audit_logs}.</li>
 * </ul>
 */
@Entity
@Immutable
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // BIGSERIAL
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 32)
    private AuditEventType eventType;

    @Column(name = "previous_balance", nullable = false, updatable = false,
            precision = Money.PRECISION, scale = Money.SCALE)
    private BigDecimal previousBalance;

    @Column(name = "new_balance", nullable = false, updatable = false,
            precision = Money.PRECISION, scale = Money.SCALE)
    private BigDecimal newBalance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {
        // for JPA
    }

    private AuditLog(UUID transactionId, UUID accountId, AuditEventType eventType,
                     BigDecimal previousBalance, BigDecimal newBalance) {
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.eventType = eventType;
        this.previousBalance = Money.normalize(previousBalance);
        this.newBalance = Money.normalize(newBalance);
        this.createdAt = Instant.now();
    }

    public static AuditLog record(UUID transactionId, UUID accountId, AuditEventType eventType,
                                  BigDecimal previousBalance, BigDecimal newBalance) {
        return new AuditLog(transactionId, accountId, eventType, previousBalance, newBalance);
    }

    public Long getId() {
        return id;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public AuditEventType getEventType() {
        return eventType;
    }

    public BigDecimal getPreviousBalance() {
        return previousBalance;
    }

    public BigDecimal getNewBalance() {
        return newBalance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
