package com.lifeplanner.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(description = "New Key Result of a goal. target must differ from baseline; a target below the baseline means \"decrease\".")
public record CreateKeyResultVO(
        @NotBlank(message = "required") @Size(max = 200, message = "too_long") @Schema(description = "Name, 1–200 characters.", example = "Recall@5") String name,
        @Size(max = 32, message = "too_long") @Schema(description = "Optional unit label, at most 32 characters.", example = "%") String unit,
        @NotNull(message = "required") @Schema(description = "Starting value (exact decimal); a new KR scores 0% at its baseline.", example = "20") BigDecimal baseline,
        @NotNull(message = "required") @Schema(description = "Target value (exact decimal), different from baseline.", example = "60") BigDecimal target,
        @Schema(description = "Relative weight in the goal's Outcome, > 0; default 1.", example = "1") BigDecimal weight) {
}
