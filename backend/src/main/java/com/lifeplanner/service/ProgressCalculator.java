package com.lifeplanner.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/**
 * The only place Goal progress numbers are computed. Outcome and Execution are independent:
 * there is deliberately no method that combines them.
 */
public final class ProgressCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** An active KR's unrounded score (0..100) and its weight (> 0). */
    public record WeightedScore(BigDecimal score, BigDecimal weight) {
    }

    private ProgressCalculator() {
    }

    /**
     * Weighted mean of the Goal's own active KR scores: Σ(weight × score) / Σ(weight).
     * {@code null} when the Goal has no active KR (not measured — never 0).
     */
    public static BigDecimal outcome(List<WeightedScore> keyResults) {
        if (keyResults.isEmpty()) {
            return null;
        }
        BigDecimal weighted = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (WeightedScore kr : keyResults) {
            weighted = weighted.add(kr.weight().multiply(kr.score()));
            totalWeight = totalWeight.add(kr.weight());
        }
        return weighted.divide(totalWeight, MathContext.DECIMAL128);
    }

    /** Weight of one KR as a percentage of the total active weight (unrounded). */
    public static BigDecimal weightShare(BigDecimal weight, BigDecimal totalWeight) {
        return weight.multiply(HUNDRED).divide(totalWeight, MathContext.DECIMAL128);
    }

    /**
     * Points one KR adds to Outcome: weight × score / Σweight. The contributions of a goal's active KRs sum to its
     * Outcome (before rounding), which is what the per-KR breakdown shows.
     */
    public static BigDecimal contribution(BigDecimal score, BigDecimal weight, BigDecimal totalWeight) {
        return weight.multiply(score).divide(totalWeight, MathContext.DECIMAL128);
    }

    /** 100 × done / active over unique active tasks; {@code null} when there is no active task. */
    public static BigDecimal execution(int activeTasks, int doneTasks) {
        if (activeTasks <= 0) {
            return null;
        }
        if (doneTasks < 0 || doneTasks > activeTasks) {
            throw new IllegalArgumentException("done must be within 0..active");
        }
        return BigDecimal.valueOf(doneTasks).multiply(HUNDRED).divide(BigDecimal.valueOf(activeTasks), MathContext.DECIMAL128);
    }

    /** Deterministic 2-decimal presentation (HALF_UP); null stays null. */
    public static BigDecimal round(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
