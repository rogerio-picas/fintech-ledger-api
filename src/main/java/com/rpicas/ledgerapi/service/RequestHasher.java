package com.rpicas.ledgerapi.service;

import com.rpicas.ledgerapi.domain.Money;
import com.rpicas.ledgerapi.web.dto.TransferRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Produces a stable SHA-256 fingerprint of a transfer request (100 and 100.00 hash equally). */
final class RequestHasher {

    private RequestHasher() {
    }

    static String hash(TransferRequest request) {
        String canonical = request.sourceAccountId() + "|" + request.targetAccountId() + "|"
                + Money.normalize(request.amount()).toPlainString();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
