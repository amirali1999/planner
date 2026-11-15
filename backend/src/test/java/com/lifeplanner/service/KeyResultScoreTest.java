package com.lifeplanner.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyResultScoreTest {

    private static BigDecimal score(String baseline, String target, String current) {
        return KeyResultScore.compute(new BigDecimal(baseline), new BigDecimal(target), new BigDecimal(current));
    }

    @Test
    void increasingVector1() {
        assertThat(score("20", "60", "40")).isEqualByComparingTo("50");
    }

    @Test
    void decreasingVector2() {
        assertThat(score("100", "60", "80")).isEqualByComparingTo("50");
    }

    @Test
    void baselineIsZeroPercent() {
        assertThat(score("20", "80", "20")).isEqualByComparingTo("0");
        assertThat(score("100", "60", "100")).isEqualByComparingTo("0");
    }

    @Test
    void clampsBothDirections() {
        assertThat(score("20", "60", "90")).isEqualByComparingTo("100");
        assertThat(score("20", "60", "-5")).isEqualByComparingTo("0");
        assertThat(score("100", "60", "10")).isEqualByComparingTo("100");
        assertThat(score("100", "60", "120")).isEqualByComparingTo("0");
    }

    @Test
    void exactDecimalArithmeticAndRoundedOutputOnly() {
        BigDecimal third = score("0", "3", "1");
        assertThat(third.toPlainString()).startsWith("33.3333333333");
        assertThat(KeyResultScore.round(third)).isEqualByComparingTo("33.33");
        assertThat(KeyResultScore.round(score("0", "3", "2"))).isEqualByComparingTo("66.67");
        // 0.1 + 0.2 style inputs stay exact: (0.3 - 0.1) / (0.5 - 0.1) = 50
        assertThat(score("0.1", "0.5", "0.3")).isEqualByComparingTo("50");
        assertThat(KeyResultScore.round(BigDecimal.ZERO).toPlainString()).isEqualTo("0.00");
    }

    @Test
    void equalBaselineAndTargetIsRejected() {
        assertThatThrownBy(() -> score("20", "20.0", "20")).isInstanceOf(IllegalArgumentException.class);
    }
}
