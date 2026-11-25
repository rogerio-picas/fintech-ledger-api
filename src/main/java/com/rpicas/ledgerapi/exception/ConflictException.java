package com.rpicas.ledgerapi.exception;

/** Maps to HTTP 409 Conflict: the request conflicts with the current state of a resource. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
