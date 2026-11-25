package com.rpicas.ledgerapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Stores the outcome of a request carrying an {@code Idempotency-Key} so that retries
 * receive the original response instead of being processed twice.
 * <p>
 * Rows are inserted with {@code INSERT ... ON CONFLICT DO NOTHING} (see the repository) to
 * claim the key, then completed with the response inside the same database transaction.
 */
@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {

    @Id
    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 255)
    private String idempotencyKey;

    /** SHA-256 of the canonical request, used to detect key reuse with a different payload. */
    @Column(name = "request_hash", nullable = false, updatable = false, length = 64)
    private String requestHash;

    @Column(name = "response_payload", columnDefinition = "text")
    private String responsePayload;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
        // for JPA
    }

    public void complete(int statusCode, String responsePayload) {
        if (isCompleted()) {
            throw new IllegalStateException("Idempotency record " + idempotencyKey + " is already completed");
        }
        this.statusCode = statusCode;
        this.responsePayload = responsePayload;
    }

    public boolean isCompleted() {
        return statusCode != null;
    }

    public boolean matches(String requestHash) {
        return this.requestHash.equals(requestHash);
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getResponsePayload() {
        return responsePayload;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
