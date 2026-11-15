package com.lifeplanner.model.vo;

import com.lifeplanner.model.HabitLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "New daily habit.")
public record CreateHabitVO(
        @NotBlank(message = "required") @Size(max = HabitLimits.TITLE_MAX, message = "too_long")
        @Schema(description = "Title, at most 200 characters.", example = "ورزش (۳۰ دقیقه)") String title) {
}
