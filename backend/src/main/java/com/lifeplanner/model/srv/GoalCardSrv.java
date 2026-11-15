package com.lifeplanner.model.srv;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One active Goal with its two separate measurements (never blended). */
@Schema(description = "A goal on the dashboard with its two separate measurements.")
public record GoalCardSrv(
        @Schema(description = "Goal id.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String goalId,
        @Schema(description = "Goal title.", example = "یادگیری RAG") String title,
        @Schema(description = "Status chosen by the user.", example = "doing", allowableValues = {"not_started", "doing", "done", "on_hold", "dropped"}) String status,
        @Schema(description = "Period granularity.", example = "quarterly", allowableValues = {"annual", "quarterly", "monthly", "weekly"}) String periodType,
        @Schema(description = "Period start (date-only).", example = "2026-09-23") LocalDate periodStart,
        @Schema(description = "Period end (date-only).", example = "2026-12-21") LocalDate periodEnd,
        @Schema(description = "Outcome progress 0..100 (2 decimals) from Key Results only: weighted mean of own active KRs, else equal mean of direct active children with an Outcome, else null (not measured). Never mixed with Execution.", example = "62.5") BigDecimal outcomeProgress,
        @Schema(description = "Where outcomeProgress comes from: own_key_results, child_rollup (no own KR; mean of children) or not_measured.", example = "own_key_results", allowableValues = {"own_key_results", "child_rollup", "not_measured"}) String outcomeSource,
        @Schema(description = "Number of own active KRs.", example = "2") int keyResultsCount,
        @Schema(description = "Children averaged into a child_rollup Outcome; 0 otherwise.", example = "0") int contributingChildCount,
        @Schema(description = "Latest check-in time of the own KRs; null when only baselines exist.", example = "2026-11-09T08:30:00.000Z") Instant measuredAt,
        @Schema(description = "Execution progress 0..100 (2 decimals) = done ÷ active distinct tasks whose effective goal is in this goal's active subtree; null when there are no such tasks. Never mixed with Outcome.", example = "75") BigDecimal executionProgress,
        @Schema(description = "Distinct active tasks in the goal's active subtree.", example = "4") int activeTaskCount,
        @Schema(description = "Of those, tasks with status done.", example = "3") int doneTaskCount) {
}
