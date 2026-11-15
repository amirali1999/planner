package com.lifeplanner.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.Optional;

/** Built by {@link MilestonePatchReader}: null = absent (unchanged), Optional.empty() = explicit null. */
@Schema(description = "Partial update (PATCH): absent = unchanged, null = clear (optional fields), value = set.")
public record UpdateMilestoneVO(
        @Schema(description = "New title; cannot be null.", example = "دمو نهایی") Optional<String> title,
        @Schema(description = "New description; null clears it.") Optional<String> description,
        @Schema(description = "New due date (date-only); null clears it.", example = "2026-12-01") Optional<LocalDate> dueDate,
        @Schema(description = "true marks completed (sets completedAt), false reopens it. Idempotent.", example = "true") Optional<Boolean> completed,
        @Schema(description = "true archives, false restores.", example = "false") Optional<Boolean> archived) {
}
