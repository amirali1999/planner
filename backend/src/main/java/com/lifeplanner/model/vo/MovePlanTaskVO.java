package com.lifeplanner.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(description = "Where to move a planned task (another calendar date).")
public record MovePlanTaskVO(
        @NotNull(message = "required") @Schema(description = "Target date, Gregorian ISO YYYY-MM-DD (the client's local date).", example = "2026-11-15") LocalDate toDate) {
}
