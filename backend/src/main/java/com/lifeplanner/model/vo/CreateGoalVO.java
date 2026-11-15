package com.lifeplanner.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import com.lifeplanner.model.enums.GoalStatus;
import com.lifeplanner.model.enums.PeriodType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(description = "New goal. Status is set by you and never derived from progress.")
public record CreateGoalVO(
        @NotBlank(message = "required") @Size(max = 200, message = "too_long") @Schema(description = "Title, 1–200 characters.", example = "یادگیری RAG") String title,
        @Size(max = 2000, message = "too_long") @Schema(description = "Optional description, at most 2000 characters.") String description,
        @NotNull(message = "required") @Schema(description = "Period granularity. A parent must be strictly coarser than its child. Not editable later.", example = "quarterly") PeriodType periodType,
        @NotNull(message = "required") @Schema(description = "First day of the period (date-only, inclusive).", example = "2026-09-23") LocalDate periodStart,
        @NotNull(message = "required") @Schema(description = "Last day of the period (date-only, inclusive); not before periodStart.", example = "2026-12-21") LocalDate periodEnd,
        @Schema(description = "Optional deadline (date-only). May lie outside the period: that only produces a warning.", example = "2026-12-15") LocalDate deadline,
        @Schema(description = "Initial status; default not_started.", example = "not_started") GoalStatus status,
        @Schema(description = "Optional parent goal: one of your active goals with a strictly coarser periodType. Omit for a standalone goal.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String parentGoalId) {
}
