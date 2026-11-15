package com.lifeplanner.model.srv;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Contract response; {@code createdEntity*} are null for reference/someday/discard. */
@Schema(description = "Result of a triage.")
public record ProcessInboxItemSrv(@Schema(description = "The triaged item.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String inboxItemId, @Schema(description = "Always processed.", example = "processed", allowableValues = {"processed"}) String status, @Schema(description = "The decision taken.", example = "task", allowableValues = {"task", "goal", "focus_item", "reference", "someday", "discard"}) String decision, @Schema(description = "Type of the created entity; null for reference/someday/discard.", example = "task", allowableValues = {"task", "goal", "focus_item"}) String createdEntityType,
                              @Schema(description = "Id of the created entity; null for reference/someday/discard.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String createdEntityId, @Schema(description = "When it was processed (UTC).", example = "2026-11-09T08:30:00.000Z") Instant processedAt) {
}
