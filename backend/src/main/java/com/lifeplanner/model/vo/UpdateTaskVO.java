package com.lifeplanner.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import com.lifeplanner.model.enums.TaskPriority;
import com.lifeplanner.model.enums.TaskStatus;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Partial update, built by {@link TaskPatchReader} (not bound by Jackson directly).
 * For each field: {@code null} (absent from JSON) = leave unchanged;
 * {@code Optional.empty()} (JSON null) = clear; {@code Optional.of(v)} = set.
 * {@code archived} archives (true) or restores (false) the task.
 */
@Schema(description = "Partial update (PATCH): absent field = unchanged, null = clear (optional fields only), value = set.")
public record UpdateTaskVO(
        @Schema(description = "New title, 1–200 characters; cannot be null.", example = "مطالعه فصل سوم") Optional<String> title,
        @Schema(description = "New description; null clears it.") Optional<String> description,
        @Schema(description = "New direct goal (one of your active goals); null removes the direct link.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") Optional<String> goalId,
        @Schema(description = "New focus item (one of your active focus items); null removes the link.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") Optional<String> focusItemId,
        @Schema(description = "New status; cannot be null.", example = "done") Optional<TaskStatus> status,
        @Schema(description = "New priority (low, medium or high); cannot be null — every task has a priority.", example = "high") Optional<TaskPriority> priority,
        @Schema(description = "New estimate in whole minutes (0–525600); null clears it.", example = "120") Optional<Integer> estimatedMinutes,
        @Schema(description = "New due date (date-only); null clears it.", example = "2026-11-20") Optional<LocalDate> dueDate,
        @Schema(description = "New blocked reason; null clears it.", example = "منتظر بازخورد") Optional<String> blockedReason,
        @Schema(description = "true archives the task (it stops counting for Execution and plans), false restores it.", example = "false") Optional<Boolean> archived) {
}
