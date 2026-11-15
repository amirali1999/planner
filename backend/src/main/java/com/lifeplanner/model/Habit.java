package com.lifeplanner.model;

import java.time.Instant;

/** A daily habit of the current user (root table habits). */
public record Habit(String id, String title, Instant archivedAt, Instant createdAt, Instant updatedAt) {

    public boolean isArchived() {
        return archivedAt != null;
    }
}
