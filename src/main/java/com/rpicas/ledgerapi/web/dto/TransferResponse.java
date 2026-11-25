package com.rpicas.ledgerapi.web.dto;

import com.rpicas.ledgerapi.domain.Transaction;
import com.rpicas.ledgerapi.domain.TransactionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Ledger transaction record representing a transfer or compensating reversal")
public record TransferResponse(
        @Schema(description = "Unique transaction identifier")
        UUID id,
        @Schema(description = "Debited source account ID")
        UUID sourceAccountId,
        @Schema(description = "Credited target account ID")
        UUID targetAccountId,
        @Schema(description = "Transferred amount with NUMERIC(19, 4) precision", example = "150.5000")
        BigDecimal amount,
        @Schema(description = "Transaction status (COMPLETED, FAILED, REVERSED)")
        TransactionStatus status,
        @Schema(description = "Original transaction ID when this transaction is a compensating reversal; null otherwise")
        UUID reversalOfId,
        @Schema(description = "UTC timestamp when the transaction was recorded")
        Instant createdAt) {

    public static TransferResponse from(Transaction tx) {
        return new TransferResponse(tx.getId(), tx.getSourceAccountId(), tx.getTargetAccountId(),
                tx.getAmount(), tx.getStatus(), tx.getReversalOfId(), tx.getCreatedAt());
    }
}
