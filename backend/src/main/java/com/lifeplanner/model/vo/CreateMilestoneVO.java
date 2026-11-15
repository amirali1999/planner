package com.lifeplanner.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import com.lifeplanner.model.MilestoneLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(description = "New milestone (delivery checkpoint) of a goal. Milestones never affect progress.")
public record CreateMilestoneVO(
        @NotBlank(message = "required") @Size(max = MilestoneLimits.TITLE_MAX, message = "too_long") @Schema(description = "Title, 1–200 characters.", example = "نسخه اول دمو") String title,
        @Size(max = MilestoneLimits.DESCRIPTION_MAX, message = "too_long") @Schema(description = "Optional description, at most 2000 characters.") String description,
        @Schema(description = "Optional due date (date-only).", example = "2026-11-30") LocalDate dueDate) {
}
