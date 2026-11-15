package com.lifeplanner.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Adds an existing task to the daily plan of the date in the path.")
public record AddPlanItemVO(@NotBlank(message = "required") @Schema(description = "Id of one of your tasks (archived tasks are refused).", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String taskId) {
}
