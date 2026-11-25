package com.rpicas.ledgerapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_name", nullable = false)
    private String ownerName;

    @Column(name = "iban", nullable = false, unique = true, length = 34)
    private String iban;

    @Column(name = "balance", nullable = false, precision = Money.PRECISION, scale = Money.SCALE)
    private BigDecimal balance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() {
        // for JPA
    }

    public Account(String ownerName, String iban, BigDecimal openingBalance) {
        BigDecimal normalized = Money.normalize(openingBalance);
        if (normalized.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Opening balance must not be negative");
        }
        this.ownerName = ownerName;
        this.iban = iban;
        this.balance = normalized;
        this.createdAt = Instant.now();
    }

    /** Removes funds. Callers must hold a pessimistic lock on this row. */
    public void debit(BigDecimal amount) {
        BigDecimal value = requirePositive(amount);
        if (balance.compareTo(value) < 0) {
            throw new IllegalStateException("Debit would overdraw account " + id);
        }
        this.balance = balance.subtract(value);
    }

    /** Adds funds. Callers must hold a pessimistic lock on this row. */
    public void credit(BigDecimal amount) {
        this.balance = balance.add(requirePositive(amount));
    }

    public boolean hasSufficientFunds(BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }

    private static BigDecimal requirePositive(BigDecimal amount) {
        BigDecimal value = Money.normalize(amount);
        if (!Money.isPositive(value)) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        return value;
    }

    public UUID getId() {
        return id;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getIban() {
        return iban;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
