package com.lifeplanner.model.srv;

import com.lifeplanner.model.FocusItemNote;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "A free-text note of a focus item (not a task: no status, no planning, no progress).")
public record FocusItemNoteSrv(
        @Schema(description = "Note id.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String id,
        @Schema(description = "The focus item it belongs to.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b11") String focusItemId,
        @Schema(description = "Note text (plain, multi-line).", example = "فصل ۳ را دوباره بخوانم؛ مثال‌های RAG مهم‌اند.") String body,
        @Schema(description = "Creation time (UTC).", example = "2026-11-12T08:30:00.000Z") Instant createdAt,
        @Schema(description = "Last change (UTC).", example = "2026-11-12T08:30:00.000Z") Instant updatedAt) {

    public static FocusItemNoteSrv from(FocusItemNote n) {
        return new FocusItemNoteSrv(n.id(), n.focusItemId(), n.body(), n.createdAt(), n.updatedAt());
    }
}
