package com.lifeplanner.model.srv;

import io.swagger.v3.oas.annotations.media.Schema;
import com.lifeplanner.model.KeyResult;
import com.lifeplanner.service.KeyResultScore;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * {@code current} is the latest check-in value or the baseline; {@code currentSource} says which.
 * {@code score} is rounded to 2 decimals; a KR with no check-ins is explicitly 0.00 (measured at baseline).
 */
@Schema(description = "A Key Result with its derived current value and score.")
public record KeyResultSrv(
        @Schema(description = "KR id.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String id,
        @Schema(description = "Goal the KR belongs to.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String goalId,
        @Schema(description = "Name.", example = "Recall@5") String name,
        @Schema(description = "Unit; null when none.", example = "%") String unit,
        @Schema(description = "Baseline value.", example = "20") BigDecimal baseline,
        @Schema(description = "Target value (below baseline = decreasing KR).", example = "60") BigDecimal target,
        @Schema(description = "Weight in the goal's Outcome.", example = "1") BigDecimal weight,
        @Schema(description = "Latest check-in value (by measuredAt, createdAt, id) or the baseline when there is none.", example = "40") BigDecimal current,
        @Schema(description = "Whether current comes from a check-in or is the baseline.", example = "check_in", allowableValues = {"check_in", "baseline"}) String currentSource,
        @Schema(description = "measuredAt of that check-in; null when current is the baseline.", example = "2026-11-09T08:30:00.000Z") Instant currentMeasuredAt,
        @Schema(description = "Progress 0..100 (2 decimals) from baseline toward target, clamped; 0 at the baseline.", example = "50") BigDecimal score,
        @Schema(description = "When archived (UTC); null while active. Archived KRs do not count.") Instant archivedAt,
        @Schema(description = "Creation time (UTC).", example = "2026-11-09T08:30:00.000Z") Instant createdAt,
        @Schema(description = "Last change (UTC).", example = "2026-11-09T08:30:00.000Z") Instant updatedAt) {

    public static KeyResultSrv from(KeyResult kr) {
        return new KeyResultSrv(kr.id(), kr.goalId(), kr.name(), kr.unit(),
                kr.baseline(), kr.target(), kr.weight(),
                kr.current(), kr.latestValue() != null ? "check_in" : "baseline", kr.latestMeasuredAt(),
                KeyResultScore.round(kr.score()),
                kr.archivedAt(), kr.createdAt(), kr.updatedAt());
    }
}
