package com.lifeplanner.model.srv;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * One goal in the tree with its two separate measurements (never blended).
 * {@code detachedFromParent}: the goal has a parent that the current filter excludes (e.g. an archived parent of
 * an active child), so it is shown as a root instead of being hidden.
 */
@Schema(description = "A goal in the tree with its measurements and its children.")
public record GoalTreeNodeSrv(
        @Schema(description = "Goal id.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String id,
        @Schema(description = "Title.", example = "یادگیری RAG") String title,
        @Schema(description = "Status chosen by the user.", example = "doing", allowableValues = {"not_started", "doing", "done", "on_hold", "dropped"}) String status,
        @Schema(description = "Period granularity.", example = "quarterly", allowableValues = {"annual", "quarterly", "monthly", "weekly"}) String periodType,
        @Schema(description = "Period start (date-only).", example = "2026-09-23") LocalDate periodStart,
        @Schema(description = "Period end (date-only).", example = "2026-12-21") LocalDate periodEnd,
        @Schema(description = "Deadline (date-only); null when none.", example = "2026-12-15") LocalDate deadline,
        @Schema(description = "Parent goal id; null for a root.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String parentGoalId,
        @Schema(description = "true when the goal has a parent that the current archive filter excludes; it is then shown as a root instead of being hidden.", example = "false") boolean detachedFromParent,
        @Schema(description = "When archived (UTC); null while active.") Instant archivedAt,
        @Schema(description = "Outcome progress 0..100 (2 decimals) from Key Results only: weighted mean of own active KRs, else equal mean of direct active children with an Outcome, else null (not measured). Never mixed with Execution.", example = "62.5") BigDecimal outcomeProgress,
        @Schema(description = "Where outcomeProgress comes from: own_key_results, child_rollup (no own KR; mean of children) or not_measured.", example = "own_key_results", allowableValues = {"own_key_results", "child_rollup", "not_measured"}) String outcomeSource,
        @Schema(description = "Number of own active KRs.", example = "1") int keyResultsCount,
        @Schema(description = "Children averaged into a child_rollup Outcome; 0 otherwise.", example = "0") int contributingChildCount,
        @Schema(description = "Execution progress 0..100 (2 decimals) = done ÷ active distinct tasks whose effective goal is in this goal's active subtree; null when there are no such tasks. Never mixed with Outcome.", example = "75") BigDecimal executionProgress,
        @Schema(description = "Distinct active tasks in the active subtree.", example = "4") int activeTaskCount,
        @Schema(description = "Of those, tasks with status done.", example = "3") int doneTaskCount,
        @Schema(description = "Child goals in stable order (period start, coarser first, creation time, id).") List<GoalTreeNodeSrv> children) {
}
