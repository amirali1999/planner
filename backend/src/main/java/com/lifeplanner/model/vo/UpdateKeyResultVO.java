package com.lifeplanner.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * Partial update built by {@link KeyResultPatchReader}: null = absent (unchanged), Optional.empty() = explicit null.
 * Changing baseline, target or weight is a recalibration and requires {@code note}.
 */
@Schema(description = "Partial update (PATCH) of a Key Result. Changing baseline, target or weight is a recalibration: note is then required and the change is audited. Check-ins are never touched.")
public record UpdateKeyResultVO(
        @Schema(description = "New name, 1–200 characters; cannot be null.", example = "Recall@10") Optional<String> name,
        @Schema(description = "New unit; null clears it.", example = "%") Optional<String> unit,
        @Schema(description = "New baseline (recalibration); cannot be null.", example = "25") Optional<BigDecimal> baseline,
        @Schema(description = "New target (recalibration), different from baseline; cannot be null.", example = "70") Optional<BigDecimal> target,
        @Schema(description = "New weight > 0 (recalibration); cannot be null.", example = "2") Optional<BigDecimal> weight,
        @Schema(description = "Reason for the change; required for a recalibration, at most 1000 characters.", example = "benchmark changed") Optional<String> note) {
}
