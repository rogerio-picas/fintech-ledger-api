package com.rpicas.ledgerapi.exception;

import java.util.UUID;

public class TransactionNotFoundException extends ResourceNotFoundException {
    public TransactionNotFoundException(UUID transactionId) {
        super("Transaction " + transactionId + " not found");
    }
}
