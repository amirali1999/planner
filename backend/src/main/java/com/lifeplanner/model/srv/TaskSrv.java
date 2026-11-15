package com.lifeplanner.model.srv;

import io.swagger.v3.oas.annotations.media.Schema;
import com.lifeplanner.model.EffectiveGoal;
import com.lifeplanner.model.Task;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Schema(description = "A task with its stored links and its derived goal attribution.")
public record TaskSrv(
        @Schema(description = "Task id.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String id,
        @Schema(description = "Title.", example = "مطالعه فصل دوم") String title,
        @Schema(description = "Description; null when none.") String description,
        @Schema(description = "Status (only three exist).", example = "doing", allowableValues = {"not_started", "doing", "done"}) String status,
        @Schema(description = "Priority; always set (default low).", example = "medium", allowableValues = {"low", "medium", "high"}) String priority,
        @Schema(description = "Estimate in minutes; null when none.", example = "90") Integer estimatedMinutes,
        @Schema(description = "Due date (date-only); null when none.", example = "2026-11-12") LocalDate dueDate,
        @Schema(description = "Why the task is blocked; null when not blocked.") String blockedReason,
        @Schema(description = "Stored direct goal link; null when none.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String goalId,
        @Schema(description = "Stored focus item link; null when none.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String focusItemId,
        @Schema(description = "When archived (UTC); null while active.") Instant archivedAt,
        @Schema(description = "Creation time (UTC).", example = "2026-11-09T08:30:00.000Z") Instant createdAt,
        @Schema(description = "Last change (UTC).", example = "2026-11-09T08:30:00.000Z") Instant updatedAt,
        @Schema(description = "When the task became done (UTC); null while not done. Set and cleared by the server.", example = "2026-11-09T17:05:00.000Z") Instant completedAt,
        @Schema(description = "Goal of the linked focus item (derived); null when none.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String focusItemGoalId,
        @Schema(description = "Goal the task counts for: goalId if set, else focusItemGoalId, else null.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String effectiveGoalId,
        @Schema(description = "Where effectiveGoalId comes from; null when the task counts for no goal.", example = "direct", allowableValues = {"direct", "focus_item"}) String effectiveGoalSource,
        @Schema(description = "Non-blocking notices, e.g. goal_differs_from_focus_item (direct goal wins).") List<TaskWarningSrv> warnings) {

    /**
     * {@code goalId} is the stored direct link; {@code focusItemGoalId} the linked focus item's goal (derived);
     * {@code effectiveGoalId}/{@code effectiveGoalSource} ("direct" | "focus_item" | null) the attribution used by
     * goal task lists and Execution. A direct/focus disagreement is a warning, never an error.
     */
    public static TaskSrv from(Task t) {
        EffectiveGoal eg = t.effectiveGoal();
        List<TaskWarningSrv> warnings = eg.conflictsWithFocusItem()
                ? List.of(new TaskWarningSrv("goal_differs_from_focus_item",
                        "هدف مستقیم این کار با هدف آیتم تمرکز آن فرق دارد؛ هدف مستقیم ملاک است.",
                        Map.of("goalId", t.goalId(), "focusItemGoalId", t.focusItemGoalId())))
                : List.of();
        return new TaskSrv(t.id(), t.title(), t.description(), t.status().code(),
                t.priority() == null ? null : t.priority().code(), t.estimatedMinutes(), t.dueDate(),
                t.blockedReason(), t.goalId(), t.focusItemId(), t.archivedAt(), t.createdAt(), t.updatedAt(),
                t.completedAt(), t.focusItemGoalId(), eg.goalId(), eg.source() == null ? null : eg.source().code(), warnings);
    }
}
