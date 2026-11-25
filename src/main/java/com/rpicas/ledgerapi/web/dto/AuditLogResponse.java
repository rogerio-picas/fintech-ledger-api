package com.rpicas.ledgerapi.web.dto;

import com.rpicas.ledgerapi.domain.AuditEventType;
import com.rpicas.ledgerapi.domain.AuditLog;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Immutable append-only audit trail entry recording a balance transition or failed transfer attempt")
public record AuditLogResponse(
        @Schema(description = "Sequential BIGSERIAL audit entry identifier", example = "1")
        Long id,
        @Schema(description = "Associated transaction ID")
        UUID transactionId,
        @Schema(description = "Account ID affected by this event")
        UUID accountId,
        @Schema(description = "Audit event type (DEBIT, CREDIT, TRANSFER_FAILED, REVERSAL_DEBIT, REVERSAL_CREDIT)")
        AuditEventType eventType,
        @Schema(description = "Account balance immediately before this event", example = "1000.0000")
        BigDecimal previousBalance,
        @Schema(description = "Account balance immediately after this event", example = "850.0000")
        BigDecimal newBalance,
        @Schema(description = "UTC timestamp when the audit entry was recorded")
        Instant createdAt) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getTransactionId(),
                log.getAccountId(),
                log.getEventType(),
                log.getPreviousBalance(),
                log.getNewBalance(),
                log.getCreatedAt());
    }
}
