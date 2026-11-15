package com.lifeplanner.model.srv;

import io.swagger.v3.oas.annotations.media.Schema;
import com.lifeplanner.model.Milestone;
import java.time.Instant;
import java.time.LocalDate;

/** {@code completed} mirrors {@code completedAt != null}. */
@Schema(description = "A milestone (delivery checkpoint) of a goal.")
public record MilestoneSrv(
        @Schema(description = "Milestone id.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String id,
        @Schema(description = "Goal id.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String goalId,
        @Schema(description = "Title.", example = "نسخه اول دمو") String title,
        @Schema(description = "Description; null when none.") String description,
        @Schema(description = "Due date (date-only); null when none.", example = "2026-11-30") LocalDate dueDate,
        @Schema(description = "Whether it is completed (completedAt is set).", example = "false") boolean completed,
        @Schema(description = "When it was completed (UTC); null while open.") Instant completedAt,
        @Schema(description = "When archived (UTC); null while active.") Instant archivedAt,
        @Schema(description = "Creation time (UTC).", example = "2026-11-09T08:30:00.000Z") Instant createdAt,
        @Schema(description = "Last change (UTC).", example = "2026-11-09T08:30:00.000Z") Instant updatedAt) {

    public static MilestoneSrv from(Milestone m) {
        return new MilestoneSrv(m.id(), m.goalId(), m.title(), m.description(), m.dueDate(), m.isCompleted(),
                m.completedAt(), m.archivedAt(), m.createdAt(), m.updatedAt());
    }
}
