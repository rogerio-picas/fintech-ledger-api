package com.rpicas.ledgerapi.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Request payload to transfer funds between two accounts")
public record TransferRequest(
        @Schema(description = "UUID of the account to debit")
        @NotNull UUID sourceAccountId,

        @Schema(description = "UUID of the account to credit (must differ from sourceAccountId)")
        @NotNull UUID targetAccountId,

        @Schema(description = "Positive transfer amount with at most 4 decimal places", example = "150.5000")
        @NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal amount) {
}
