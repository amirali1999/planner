package com.lifeplanner.model;

import com.lifeplanner.model.enums.FocusKind;
import com.lifeplanner.model.enums.FocusStatus;
import java.time.Instant;

/**
 * A project, book, course or area of action. The Goal link is optional and single (many-to-many is out of MVP v0 scope).
 * Archiving a Goal never touches its Focus Items. {@code position}: manual order inside its goal (1 = top), null without goal.
 */
public record FocusItem(
        String id,
        String title,
        FocusKind kind,
        String description,
        String goalId,
        FocusStatus status,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt,
        Integer position) {

    /** Before insert the position is not known yet (the repository appends it). */
    public FocusItem(String id, String title, FocusKind kind, String description, String goalId, FocusStatus status,
                     Instant archivedAt, Instant createdAt, Instant updatedAt) {
        this(id, title, kind, description, goalId, status, archivedAt, createdAt, updatedAt, null);
    }

    public boolean isArchived() {
        return archivedAt != null;
    }
}
