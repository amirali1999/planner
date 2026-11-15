package com.lifeplanner.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * KR score = clamp(100 × (current − baseline) / (target − baseline), 0, 100).
 * Works for increasing and decreasing targets. Exact BigDecimal arithmetic (DECIMAL128); round only for output.
 */
public final class KeyResultScore {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private KeyResultScore() {
    }

    /** Unrounded score in [0, 100]. Requires baseline ≠ target (guaranteed by validation and DB CHECK). */
    public static BigDecimal compute(BigDecimal baseline, BigDecimal target, BigDecimal current) {
        BigDecimal span = target.subtract(baseline);
        if (span.signum() == 0) {
            throw new IllegalArgumentException("target must differ from baseline");
        }
        BigDecimal pct = current.subtract(baseline).multiply(HUNDRED).divide(span, MathContext.DECIMAL128);
        if (pct.signum() < 0) {
            return BigDecimal.ZERO;
        }
        return pct.compareTo(HUNDRED) > 0 ? HUNDRED : pct;
    }

    /** Deterministic 2-decimal presentation value (HALF_UP). */
    public static BigDecimal round(BigDecimal score) {
        return score.setScale(2, RoundingMode.HALF_UP);
    }
}
