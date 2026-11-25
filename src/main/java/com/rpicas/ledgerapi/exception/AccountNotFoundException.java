package com.rpicas.ledgerapi.exception;

import java.util.UUID;

public class AccountNotFoundException extends ResourceNotFoundException {
    public AccountNotFoundException(UUID accountId) {
        super("Account " + accountId + " not found!");
    }
}
