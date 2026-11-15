package com.lifeplanner.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import com.lifeplanner.model.TaskLimits;
import com.lifeplanner.model.enums.TaskPriority;
import com.lifeplanner.model.enums.TaskStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(description = "New task. A task may be standalone, linked to a goal directly, to a focus item, or both (the direct goal wins for attribution).")
public record CreateTaskVO(
        @NotBlank(message = "required") @Size(max = TaskLimits.TITLE_MAX, message = "too_long") @Schema(description = "Title, 1–200 characters.", example = "مطالعه فصل دوم") String title,
        @Size(max = TaskLimits.DESCRIPTION_MAX, message = "too_long") @Schema(description = "Optional description, at most 2000 characters.") String description,
        @Schema(description = "Optional direct goal (one of your active goals).", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String goalId,
        @Schema(description = "Optional focus item (one of your active focus items).", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String focusItemId,
        @Schema(description = "Initial status; default not_started. Only three statuses exist; use blockedReason for blocked work.", example = "not_started") TaskStatus status,
        @Schema(description = "Priority; default low when omitted.", example = "medium", defaultValue = "low") TaskPriority priority,
        @Min(value = 0, message = "must_not_be_negative") @Max(value = TaskLimits.ESTIMATE_MAX, message = "too_large")
        @Schema(description = "Optional estimate in whole minutes, 0–525600.", example = "90") Integer estimatedMinutes,
        @Schema(description = "Optional due date (date-only); independent from the days the task is planned on.", example = "2026-11-12") LocalDate dueDate,
        @Size(max = TaskLimits.BLOCKED_REASON_MAX, message = "too_long") @Schema(description = "Optional reason the task is blocked (at most 1000 characters); it does not change the status.") String blockedReason) {
}
