package com.lifeplanner.model;

import com.lifeplanner.model.enums.InboxStatus;
import java.time.Instant;

/**
 * Raw captured text awaiting triage. Until processed it has no decision and no linked Goal/Task/Focus Item;
 * the text is never parsed or interpreted.
 */
public record InboxItem(
        String id,
        String rawText,
        InboxStatus status,
        String decision,
        String createdEntityType,
        String createdEntityId,
        Instant processedAt,
        Instant capturedAt,
        Instant createdAt,
        Instant updatedAt) {
}
