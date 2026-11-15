package com.lifeplanner.model.srv;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;

@Schema(description = "An activity that the current user actually performed during one local-day interval.")
public record ActivityLogSrv(
        @Schema(description = "Activity log id.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String id,
        @Schema(description = "Local calendar day (date-only).", example = "2026-11-14") LocalDate date,
        @Schema(description = "What was actually done.", example = "مطالعه فصل چهارم") String title,
        @Schema(description = "Category code.", example = "study", allowableValues = {"work", "study", "rest", "personal"}) String category,
        @Schema(description = "Start, minutes after local midnight.", example = "600") int startMinute,
        @Schema(description = "End (exclusive), minutes after local midnight.", example = "690") int endMinute,
        @Schema(description = "Creation time (UTC).", example = "2026-11-14T07:00:00.000Z") Instant createdAt,
        @Schema(description = "Last edit time (UTC).", example = "2026-11-14T07:30:00.000Z") Instant updatedAt,
        @Schema(description = "Optional linked active task.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String taskId) {

    public static ActivityLogSrv from(com.lifeplanner.model.ActivityLog log) {
        return new ActivityLogSrv(log.id(), log.date(), log.title(), log.category().code(), log.startMinute(),
                log.endMinute(), log.createdAt(), log.updatedAt(), log.taskId());
    }
}
