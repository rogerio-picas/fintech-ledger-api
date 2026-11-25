package com.rpicas.ledgerapi.web.dto;

import com.rpicas.ledgerapi.domain.Account;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Bank account details and current ledger balance")
public record AccountResponse(
        @Schema(description = "Unique account identifier")
        UUID id,
        @Schema(description = "Account owner name", example = "Alice Silva")
        String ownerName,
        @Schema(description = "Unique IBAN", example = "PT50000201231234567890154")
        String iban,
        @Schema(description = "Current balance with NUMERIC(19, 4) precision", example = "850.0000")
        BigDecimal balance,
        @Schema(description = "UTC timestamp when the account was created")
        Instant createdAt) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getOwnerName(), account.getIban(),
                account.getBalance(), account.getCreatedAt());
    }
}
