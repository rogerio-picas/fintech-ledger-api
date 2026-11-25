package com.rpicas.ledgerapi.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Monetary helpers. Every amount in the ledger is a {@link BigDecimal} with
 * scale 4,
 * matching the NUMERIC(19,4) database columns. Floating point types are never
 * used.
 */
public final class Money {

    public static final int PRECISION = 19;
    public static final int SCALE = 4;

    private Money() {
    }

    /**
     * Normalises an amount to scale 4. Throws {@link ArithmeticException} if the
     * value
     * carries more than 4 fractional digits, instead of silently rounding money.
     */
    public static BigDecimal normalize(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null.");
        }
        return amount.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static boolean isPositive(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }
}
