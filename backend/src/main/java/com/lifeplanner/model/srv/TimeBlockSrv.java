package com.lifeplanner.model.srv;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;

@Schema(description = "A block of planned time on one local day (weekly time-box). task is the live linked task, not a copy.")
public record TimeBlockSrv(
        @Schema(description = "Time block id.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String id,
        @Schema(description = "Local calendar day (date-only).", example = "2026-11-14") LocalDate date,
        @Schema(description = "Start, minutes after local midnight.", example = "540") int startMinute,
        @Schema(description = "End (exclusive), minutes after local midnight.", example = "600") int endMinute,
        @Schema(description = "Own title; null when the block only shows its task.", example = "مرور ایمیل و پیام‌ها") String title,
        @Schema(description = "Linked task id; null for a free block.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String taskId,
        @Schema(description = "The linked task (live, may be archived); null for a free block.") TaskSrv task,
        @Schema(description = "Colour key.", example = "teal", allowableValues = {"teal", "blue", "purple", "green", "olive", "amber", "rose", "slate"}) String color,
        @Schema(description = "Creation time (UTC).", example = "2026-11-12T08:30:00.000Z") Instant createdAt,
        @Schema(description = "Last change (UTC).", example = "2026-11-12T08:30:00.000Z") Instant updatedAt) {
}
