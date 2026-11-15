package com.lifeplanner.db;

import java.math.BigDecimal;

/**
 * Persistence strategy for KR baseline/target/weight and check-in values.
 * <p>
 * SQLite has no fixed-point type and REAL would introduce binary floating-point error, so decimals
 * are stored as canonical plain TEXT (e.g. {@code "20"}, {@code "-1.5"}, {@code "0.333333"}) and read
 * back as {@link BigDecimal}. Canonical means: no exponent, no trailing fractional zeros, no '+'.
 * Limits: at most {@value #MAX_SCALE} fractional digits and {@value #MAX_INTEGER_DIGITS} integer digits;
 * values outside are rejected rather than silently rounded. NaN/Infinity cannot be represented.
 */
public final class DecimalCodec {

    public static final int MAX_SCALE = 6;
    public static final int MAX_INTEGER_DIGITS = 15;

    private DecimalCodec() {
    }

    public static boolean isStorable(BigDecimal value) {
        if (value == null) {
            return false;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        int integerDigits = stripped.precision() - stripped.scale();
        return stripped.scale() <= MAX_SCALE && integerDigits <= MAX_INTEGER_DIGITS;
    }

    public static String toDb(BigDecimal value) {
        if (!isStorable(value)) {
            throw new IllegalArgumentException("Decimal not storable (null, >" + MAX_SCALE
                    + " fractional digits or >" + MAX_INTEGER_DIGITS + " integer digits)");
        }
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() < 0) {
            stripped = stripped.setScale(0);
        }
        return stripped.toPlainString();
    }

    public static BigDecimal fromDb(String text) {
        return text == null ? null : new BigDecimal(text);
    }
}
