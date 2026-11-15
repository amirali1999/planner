package com.lifeplanner.model.srv;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "A daily habit with the local days (inside the requested range) on which it was done.")
public record HabitSrv(
        @Schema(description = "Habit id.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String id,
        @Schema(description = "Habit title.", example = "مطالعه روزانه (۳۰ دقیقه)") String title,
        @ArraySchema(arraySchema = @Schema(description = "Days marked done inside from..to (ascending; empty outside a range request)."),
                schema = @Schema(type = "string", format = "date", example = "2026-11-10")) List<LocalDate> doneDates,
        @Schema(description = "When it was archived (UTC); null while active.", example = "2026-11-12T08:30:00.000Z") Instant archivedAt,
        @Schema(description = "Creation time (UTC).", example = "2026-11-12T08:30:00.000Z") Instant createdAt,
        @Schema(description = "Last change (UTC).", example = "2026-11-12T08:30:00.000Z") Instant updatedAt) {
}
