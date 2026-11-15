package com.lifeplanner.service;

import com.lifeplanner.service.ProgressCalculator.WeightedScore;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProgressCalculatorTest {

    private static WeightedScore kr(String score, String weight) {
        return new WeightedScore(new BigDecimal(score), new BigDecimal(weight));
    }

    @Test
    void vector3WeightedOutcome() {
        assertThat(ProgressCalculator.outcome(List.of(kr("100", "1"), kr("0", "3")))).isEqualByComparingTo("25");
    }

    @Test
    void vector4OutcomeAndExecutionStaySeparate() {
        BigDecimal outcome = ProgressCalculator.outcome(List.of(kr("100", "1")));
        BigDecimal execution = ProgressCalculator.execution(4, 2);
        assertThat(outcome).isEqualByComparingTo("100");
        assertThat(execution).isEqualByComparingTo("50");
        // There is no API to blend them; the domain forbids (100 + 50) / 2 = 75.
    }

    @Test
    void acceptanceKr50AndThreeOfFourDone() {
        BigDecimal outcome = ProgressCalculator.outcome(List.of(new WeightedScore(
                KeyResultScore.compute(new BigDecimal("20"), new BigDecimal("60"), new BigDecimal("40")), BigDecimal.ONE)));
        assertThat(ProgressCalculator.round(outcome)).isEqualByComparingTo("50.00");
        assertThat(ProgressCalculator.round(ProgressCalculator.execution(4, 3))).isEqualByComparingTo("75.00");
    }

    @Test
    void decreasingKrFeedsOutcome() {
        BigDecimal score = KeyResultScore.compute(new BigDecimal("100"), new BigDecimal("60"), new BigDecimal("80"));
        assertThat(ProgressCalculator.outcome(List.of(new WeightedScore(score, BigDecimal.ONE)))).isEqualByComparingTo("50");
    }

    @Test
    void vector6EmptyDenominatorsAreNullNotZero() {
        assertThat(ProgressCalculator.outcome(List.of())).isNull();
        assertThat(ProgressCalculator.execution(0, 0)).isNull();
        assertThat(ProgressCalculator.round(null)).isNull();
    }

    @Test
    void measuredZeroIsZeroNotNull() {
        assertThat(ProgressCalculator.outcome(List.of(kr("0", "1")))).isEqualByComparingTo("0");
        assertThat(ProgressCalculator.execution(2, 0)).isEqualByComparingTo("0");
    }

    @Test
    void roundsOnlyAtTheEnd() {
        // scores 1/3 and 2/3 of 100 (unrounded) with weights 1 and 2 → (33.33… + 133.33…) / 3 = 55.555… → 55.56
        BigDecimal third = KeyResultScore.compute(BigDecimal.ZERO, new BigDecimal("3"), BigDecimal.ONE);
        BigDecimal twoThirds = KeyResultScore.compute(BigDecimal.ZERO, new BigDecimal("3"), new BigDecimal("2"));
        BigDecimal outcome = ProgressCalculator.outcome(List.of(new WeightedScore(third, BigDecimal.ONE),
                new WeightedScore(twoThirds, new BigDecimal("2"))));
        assertThat(ProgressCalculator.round(outcome)).isEqualByComparingTo("55.56");
        assertThat(ProgressCalculator.round(ProgressCalculator.execution(3, 1))).isEqualByComparingTo("33.33");
    }

    @Test
    void breakdownContributionsSumToOutcome() {
        BigDecimal total = new BigDecimal("4");
        assertThat(ProgressCalculator.weightShare(BigDecimal.ONE, total)).isEqualByComparingTo("25");
        assertThat(ProgressCalculator.weightShare(new BigDecimal("3"), total)).isEqualByComparingTo("75");
        BigDecimal c1 = ProgressCalculator.contribution(new BigDecimal("100"), BigDecimal.ONE, total);
        BigDecimal c2 = ProgressCalculator.contribution(BigDecimal.ZERO, new BigDecimal("3"), total);
        assertThat(c1.add(c2)).isEqualByComparingTo(ProgressCalculator.outcome(List.of(kr("100", "1"), kr("0", "3"))));
    }

    @Test
    void doneCannotExceedActive() {
        assertThatThrownBy(() -> ProgressCalculator.execution(2, 3)).isInstanceOf(IllegalArgumentException.class);
    }
}
