package com.lifeplanner.model.vo;

import com.lifeplanner.model.TimeBlockLimits;
import com.lifeplanner.model.enums.TimeBlockColor;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(description = "New time block on one local day. Give a taskId (one of your active tasks), a title, or both; a block linked to a task shows the task's title unless it has its own.")
public record CreateTimeBlockVO(
        @NotNull(message = "required") @Schema(description = "Local calendar day (date-only).", example = "2026-11-14") LocalDate date,
        @NotNull(message = "required") @Schema(description = "Start, minutes after local midnight (0–1435, multiple of 5).", example = "540") Integer startMinute,
        @NotNull(message = "required") @Schema(description = "End (exclusive), minutes after local midnight (5–1440, multiple of 5, after startMinute).", example = "600") Integer endMinute,
        @Size(max = TimeBlockLimits.TITLE_MAX, message = "too_long") @Schema(description = "Own title; required without taskId, at most 200 characters.", example = "مرور ایمیل و پیام‌ها") String title,
        @Schema(description = "Optional linked task (one of your non-archived tasks).", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String taskId,
        @Schema(description = "Colour key; default teal.", example = "blue", allowableValues = {"teal", "blue", "purple", "green", "olive", "amber", "rose", "slate"}) TimeBlockColor color) {
}
