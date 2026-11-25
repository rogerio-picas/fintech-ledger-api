package com.rpicas.ledgerapi.exception;

/** Maps to HTTP 404 Not Found. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
