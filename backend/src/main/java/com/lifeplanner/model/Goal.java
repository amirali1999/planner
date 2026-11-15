package com.lifeplanner.model;

import com.lifeplanner.model.enums.GoalStatus;
import com.lifeplanner.model.enums.PeriodType;
import java.time.Instant;
import java.time.LocalDate;

/** Persisted Goal row. {@code priority}: manual rank among the owner's active goals (1 = highest), null when archived. */
public record Goal(
        String id,
        String title,
        String description,
        PeriodType periodType,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate deadline,
        GoalStatus status,
        String parentGoalId,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt,
        Integer priority) {

    /** A goal whose priority is not (yet) known, e.g. before it is inserted. */
    public Goal(String id, String title, String description, PeriodType periodType, LocalDate periodStart,
                LocalDate periodEnd, LocalDate deadline, GoalStatus status, String parentGoalId, Instant archivedAt,
                Instant createdAt, Instant updatedAt) {
        this(id, title, description, periodType, periodStart, periodEnd, deadline, status, parentGoalId, archivedAt,
                createdAt, updatedAt, null);
    }

    public boolean isArchived() {
        return archivedAt != null;
    }
}
