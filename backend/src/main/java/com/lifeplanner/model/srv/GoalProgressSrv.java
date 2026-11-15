package com.lifeplanner.model.srv;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Two independent measurements of one Goal. Values are rounded to 2 decimals; null = not measured.
 * Outcome: own KRs, else child rollup (see {@link ProgressRollup}). Execution: distinct tasks of the active subtree
 * ({@code activeTaskCount}/{@code doneTaskCount} are subtree counts). {@code contributingChildCount} = children averaged
 * into a {@code child_rollup} Outcome (0 otherwise). There is intentionally no combined/blended field.
 */
@Schema(description = "Outcome and Execution of one goal, reported separately (there is no combined value).")
public record GoalProgressSrv(
        @Schema(description = "Goal id.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String goalId,
        @Schema(description = "Outcome progress 0..100 (2 decimals) from Key Results only: weighted mean of own active KRs, else equal mean of direct active children with an Outcome, else null (not measured). Never mixed with Execution.", example = "62.5") BigDecimal outcomeProgress,
        @Schema(description = "Where outcomeProgress comes from: own_key_results, child_rollup (no own KR; mean of children) or not_measured.", example = "own_key_results", allowableValues = {"own_key_results", "child_rollup", "not_measured"}) String outcomeSource,
        @Schema(description = "Number of own active KRs.", example = "2") int keyResultsCount,
        @Schema(description = "Children averaged into a child_rollup Outcome; 0 otherwise.", example = "0") int contributingChildCount,
        @Schema(description = "Latest check-in time of the own KRs; null when only baselines exist.", example = "2026-11-09T08:30:00.000Z") Instant measuredAt,
        @Schema(description = "Execution progress 0..100 (2 decimals) = done ÷ active distinct tasks whose effective goal is in this goal's active subtree; null when there are no such tasks. Never mixed with Outcome.", example = "75") BigDecimal executionProgress,
        @Schema(description = "Distinct active tasks whose effective goal is in the active subtree.", example = "4") int activeTaskCount,
        @Schema(description = "Of those, tasks with status done.", example = "3") int doneTaskCount,
        @Schema(description = "How each own active KR contributes to Outcome (empty without own KRs).") List<KeyResultContributionSrv> keyResults,
        @Schema(description = "Outcomes of the direct active children (archived/dropped children are excluded).") List<ChildOutcomeSrv> childOutcomes,
        @Schema(description = "Goals whose tasks entered Execution: this goal plus its active descendants.", example = "3") int subtreeGoalCount) {

    public static final String SOURCE_OWN_KEY_RESULTS = "own_key_results";
    public static final String SOURCE_CHILD_ROLLUP = "child_rollup";
    public static final String SOURCE_NOT_MEASURED = "not_measured";
}
