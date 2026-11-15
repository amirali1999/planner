package com.lifeplanner.model;

import com.lifeplanner.service.KeyResultScore;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Persisted KR row together with its latest check-in (by measuredAt, createdAt, id), if any.
 * {@code current} is derived, never stored.
 */
public record KeyResult(
        String id,
        String goalId,
        String name,
        String unit,
        BigDecimal baseline,
        BigDecimal target,
        BigDecimal weight,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt,
        BigDecimal latestValue,
        Instant latestMeasuredAt) {

    public boolean isArchived() {
        return archivedAt != null;
    }

    /** Latest check-in value, or baseline (an explicit 0% starting measurement) when there is none. */
    public BigDecimal current() {
        return latestValue != null ? latestValue : baseline;
    }

    public BigDecimal score() {
        return KeyResultScore.compute(baseline, target, current());
    }
}
