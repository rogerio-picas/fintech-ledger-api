package com.rpicas.ledgerapi.service;

/**
 * Result of an idempotent operation: the HTTP status and serialized JSON body, plus whether
 * it was served from the idempotency cache instead of being processed.
 */
public record IdempotentResponse(int statusCode, String body, boolean replayed) {
}
