package com.lifeplanner.db;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodecTest {

    @Test
    void decimalIsCanonicalPlainText() {
        assertThat(DecimalCodec.toDb(new BigDecimal("20.000"))).isEqualTo("20");
        assertThat(DecimalCodec.toDb(new BigDecimal("1E+3"))).isEqualTo("1000");
        assertThat(DecimalCodec.toDb(new BigDecimal("-0.500"))).isEqualTo("-0.5");
        assertThat(DecimalCodec.toDb(new BigDecimal("0.00"))).isEqualTo("0");
        assertThat(DecimalCodec.toDb(new BigDecimal("0.1"))).isEqualTo("0.1");
    }

    @Test
    void decimalRoundTripPreservesValueWithoutFloatingPointError() {
        BigDecimal v = new BigDecimal("0.1").add(new BigDecimal("0.2"));
        assertThat(DecimalCodec.fromDb(DecimalCodec.toDb(v))).isEqualByComparingTo("0.3");
        assertThat(DecimalCodec.fromDb(null)).isNull();
    }

    @Test
    void decimalBeyondConfiguredPrecisionIsRejectedNotRounded() {
        assertThat(DecimalCodec.isStorable(new BigDecimal("0.123456"))).isTrue();
        assertThat(DecimalCodec.isStorable(new BigDecimal("0.1234567"))).isFalse();
        assertThat(DecimalCodec.isStorable(new BigDecimal("999999999999999"))).isTrue();
        assertThat(DecimalCodec.isStorable(new BigDecimal("1000000000000000"))).isFalse();
        assertThat(DecimalCodec.isStorable(null)).isFalse();
        assertThatThrownBy(() -> DecimalCodec.toDb(new BigDecimal("1e-7"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void timestampIsFixedWidthUtcAndOrdersLexically() {
        String whole = UtcTimestamps.toDb(Instant.parse("2026-11-03T12:00:00Z"));
        String half = UtcTimestamps.toDb(Instant.parse("2026-11-03T12:00:00.5Z"));
        assertThat(whole).isEqualTo("2026-11-03T12:00:00.000Z");
        assertThat(half).isEqualTo("2026-11-03T12:00:00.500Z");
        assertThat(whole.compareTo(half)).isNegative();
        assertThat(UtcTimestamps.toDb(Instant.parse("2026-11-03T12:00:00.123456789Z"))).isEqualTo("2026-11-03T12:00:00.123Z");
        assertThat(UtcTimestamps.fromDb(half)).isEqualTo(Instant.parse("2026-11-03T12:00:00.5Z"));
    }

    @Test
    void timestampOutsideFourDigitYearsIsRejected() {
        assertThatThrownBy(() -> UtcTimestamps.toDb(Instant.parse("+10000-01-01T00:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
