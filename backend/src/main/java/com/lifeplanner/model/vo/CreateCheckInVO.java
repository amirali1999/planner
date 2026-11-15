package com.lifeplanner.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "New measurement of a Key Result. Check-ins are append-only (never edited or deleted).")
public record CreateCheckInVO(
        @NotNull(message = "required") @Schema(description = "Measured value in the KR's unit (exact decimal).", example = "40") BigDecimal value,
        @NotNull(message = "required") @Schema(description = "When the value was measured (UTC timestamp); the latest by measuredAt drives the KR's current value.", example = "2026-11-09T08:30:00.000Z") Instant measuredAt,
        @Size(max = 1000, message = "too_long") @Schema(description = "Optional note, at most 1000 characters.", example = "evaluation set v1") String note) {
}
