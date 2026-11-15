package com.lifeplanner.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import com.lifeplanner.model.FocusItemLimits;
import com.lifeplanner.model.enums.FocusKind;
import com.lifeplanner.model.enums.FocusStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "New focus item (project, book, course, area or other).")
public record CreateFocusItemVO(
        @NotBlank(message = "required") @Size(max = FocusItemLimits.TITLE_MAX, message = "too_long") @Schema(description = "Title, 1–200 characters.", example = "کتاب Designing ML Systems") String title,
        @NotNull(message = "required") @Schema(description = "Kind of focus item.", example = "book") FocusKind kind,
        @Size(max = FocusItemLimits.DESCRIPTION_MAX, message = "too_long") @Schema(description = "Optional description, at most 2000 characters.") String description,
        @Schema(description = "Optional goal this focus item serves (must be one of your active goals). Tasks of the focus item without their own goal count for this goal.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String goalId,
        @Schema(description = "Initial status; default not_started.", example = "not_started") FocusStatus status) {
}
