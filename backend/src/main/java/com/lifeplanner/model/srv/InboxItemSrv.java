package com.lifeplanner.model.srv;

import io.swagger.v3.oas.annotations.media.Schema;
import com.lifeplanner.model.InboxItem;
import java.time.Instant;

@Schema(description = "A captured note and, once triaged, what became of it.")
public record InboxItemSrv(
        @Schema(description = "Inbox item id.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String id,
        @Schema(description = "The note exactly as captured (plain text).", example = "کتاب RAG را بخرم") String rawText,
        @Schema(description = "unprocessed until triaged.", example = "unprocessed", allowableValues = {"unprocessed", "processed"}) String status,
        @Schema(description = "Triage decision; null while unprocessed.", example = "task", allowableValues = {"task", "goal", "focus_item", "reference", "someday", "discard"}) String decision,
        @Schema(description = "Type of the entity created by triage; null for reference/someday/discard or while unprocessed.", example = "task", allowableValues = {"task", "goal", "focus_item"}) String createdEntityType,
        @Schema(description = "Id of the created entity; null when none.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String createdEntityId,
        @Schema(description = "When it was triaged (UTC); null while unprocessed.", example = "2026-11-09T08:30:00.000Z") Instant processedAt,
        @Schema(description = "When it was captured (UTC); the list is newest first by this.", example = "2026-11-09T08:30:00.000Z") Instant capturedAt,
        @Schema(description = "Creation time (UTC).", example = "2026-11-09T08:30:00.000Z") Instant createdAt,
        @Schema(description = "Last change (UTC).", example = "2026-11-09T08:30:00.000Z") Instant updatedAt) {

    public static InboxItemSrv from(InboxItem i) {
        return new InboxItemSrv(i.id(), i.rawText(), i.status().code(), i.decision(), i.createdEntityType(),
                i.createdEntityId(), i.processedAt(), i.capturedAt(), i.createdAt(), i.updatedAt());
    }
}
