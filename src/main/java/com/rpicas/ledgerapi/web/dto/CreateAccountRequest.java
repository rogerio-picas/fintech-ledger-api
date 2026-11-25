package com.rpicas.ledgerapi.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Request payload to open a new ledger account")
public record CreateAccountRequest(
        @Schema(description = "Full name of the account owner", example = "Alice Silva")
        @NotBlank @Size(max = 255) String ownerName,

        @Schema(description = "Unique IBAN (uppercase, no spaces)", example = "PT50000201231234567890154")
        @NotBlank @Pattern(regexp = "^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$",
                message = "must be a valid IBAN (uppercase, no spaces)") String iban,

        @Schema(description = "Opening balance (non-negative, up to 4 decimal places)", example = "1000.0000")
        @NotNull @PositiveOrZero @Digits(integer = 15, fraction = 4) BigDecimal initialBalance) {
}
