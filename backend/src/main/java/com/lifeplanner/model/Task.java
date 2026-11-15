package com.lifeplanner.model;

import com.lifeplanner.model.enums.TaskPriority;
import com.lifeplanner.model.enums.TaskStatus;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Persisted Task row. {@code dueDate} is a real deadline and is unrelated to which day the task is planned on
 * (that lives on a daily plan item). {@code focusItemGoalId} is derived (joined from focus_items), never stored.
 * {@code completedAt} is maintained by the repository (set on the transition to done, cleared on reopening).
 */
public record Task(
        String id,
        String title,
        String description,
        String goalId,
        String focusItemId,
        TaskStatus status,
        TaskPriority priority,
        Integer estimatedMinutes,
        LocalDate dueDate,
        String blockedReason,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt,
        String focusItemGoalId,
        Instant completedAt) {

    /** Without the derived focus-item goal (for building rows to write; it is read back via the repository join). */
    public Task(String id, String title, String description, String goalId, String focusItemId, TaskStatus status,
                TaskPriority priority, Integer estimatedMinutes, LocalDate dueDate, String blockedReason,
                Instant archivedAt, Instant createdAt, Instant updatedAt) {
        this(id, title, description, goalId, focusItemId, status, priority, estimatedMinutes, dueDate, blockedReason,
                archivedAt, createdAt, updatedAt, null, null);
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    /** Direct goal wins over the focus item's goal; derived on every read, never stored. */
    public EffectiveGoal effectiveGoal() {
        return EffectiveGoal.resolve(goalId, focusItemGoalId);
    }
}
