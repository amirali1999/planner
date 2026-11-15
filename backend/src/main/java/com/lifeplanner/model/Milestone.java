package com.lifeplanner.model;

import java.time.Instant;
import java.time.LocalDate;

/** Delivery checkpoint of a Goal. Not a Key Result: it never feeds Outcome or Execution. */
public record Milestone(
        String id,
        String goalId,
        String title,
        String description,
        LocalDate dueDate,
        Instant completedAt,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt) {

    public boolean isCompleted() {
        return completedAt != null;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }
}
