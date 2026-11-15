package com.lifeplanner.model.srv;

import io.swagger.v3.oas.annotations.media.Schema;
import com.lifeplanner.model.FocusItem;
import java.time.Instant;

@Schema(description = "A focus item.")
public record FocusItemSrv(@Schema(description = "Focus item id.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String id, @Schema(description = "Title.", example = "کتاب Designing ML Systems") String title, @Schema(description = "Kind.", example = "book", allowableValues = {"project", "book", "course", "area", "other"}) String kind, @Schema(description = "Description; null when none.") String description, @Schema(description = "Linked goal; null when none.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String goalId, @Schema(description = "Status.", example = "doing", allowableValues = {"not_started", "doing", "done"}) String status,
                                @Schema(description = "When archived (UTC); null while active.") Instant archivedAt, @Schema(description = "Creation time (UTC).", example = "2026-11-09T08:30:00.000Z") Instant createdAt, @Schema(description = "Last change (UTC).", example = "2026-11-09T08:30:00.000Z") Instant updatedAt,
        @Schema(description = "Manual position inside its goal's list (1 = top; goals board drag handle). Set with PUT /goals/{goalId}/focus-items/order; new items are appended; null when the item has no goal.", example = "1") Integer position) {
    public static FocusItemSrv from(FocusItem f) {
        return new FocusItemSrv(f.id(), f.title(), f.kind().code(), f.description(), f.goalId(), f.status().code(),
                f.archivedAt(), f.createdAt(), f.updatedAt(), f.position());
    }
}
