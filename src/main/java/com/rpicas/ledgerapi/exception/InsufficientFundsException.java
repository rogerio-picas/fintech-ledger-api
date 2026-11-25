package com.rpicas.ledgerapi.exception;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientFundsException extends BusinessRuleException {
    public InsufficientFundsException(UUID accountId, BigDecimal requestedAmount) {
        super("Insufficient funds in account " + accountId + " for amount " + requestedAmount.toPlainString());
    }
}
