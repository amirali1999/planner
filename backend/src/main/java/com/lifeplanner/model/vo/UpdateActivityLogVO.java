package com.lifeplanner.model.vo;

import com.lifeplanner.model.ActivityLogLimits;
import com.lifeplanner.model.enums.ActivityCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(description = "Full replacement of an actual activity log's editable fields.")
public record UpdateActivityLogVO(
        @NotNull(message = "required") @Schema(description = "Local calendar day (date-only).", example = "2026-11-14") LocalDate date,
        @NotBlank(message = "required") @Size(max = ActivityLogLimits.TITLE_MAX, message = "too_long") @Schema(description = "What was actually done, 1–200 characters.", example = "مطالعه فصل چهارم") String title,
        @NotNull(message = "required") @Schema(description = "Activity category.", example = "study", allowableValues = {"work", "study", "rest", "personal"}) ActivityCategory category,
        @NotNull(message = "required") @Min(value = 0, message = "invalid_value") @Max(value = 1439, message = "invalid_value") @Schema(description = "Start, minutes after local midnight; minute precision.", example = "600") Integer startMinute,
        @NotNull(message = "required") @Min(value = 1, message = "invalid_value") @Max(value = ActivityLogLimits.DAY_MINUTES, message = "invalid_value") @Schema(description = "End (exclusive), minutes after local midnight; multiple of 5 and after startMinute.", example = "690") Integer endMinute,
        @Schema(description = "Optional task worked on; null clears.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String taskId) {
}
