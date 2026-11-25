package com.rpicas.ledgerapi.exception;

/** Maps to HTTP 422 Unprocessable Content: well-formed request that violates a business rule. */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
