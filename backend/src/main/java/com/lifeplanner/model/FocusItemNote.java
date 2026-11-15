package com.lifeplanner.model;

import java.time.Instant;

/** A free-text note of a focus item (child of focus_items; not a task). */
public record FocusItemNote(String id, String focusItemId, String body, Instant createdAt, Instant updatedAt) {
}
