package com.lifeplanner.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import com.lifeplanner.model.enums.GoalStatus;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Partial update built by {@link GoalPatchReader}: null = key absent (unchanged), Optional.empty() = explicit null
 * (clear), Optional.of(v) = set. periodType is not editable here.
 */
@Schema(description = "Partial update (PATCH): absent field = unchanged, null = clear (optional fields only), value = set. periodType cannot be changed. Changes are recorded in the goal history.")
public record UpdateGoalVO(
        @Schema(description = "New title, 1–200 characters; cannot be null.", example = "یادگیری عمیق RAG") Optional<String> title,
        @Schema(description = "New description; null clears it.") Optional<String> description,
        @Schema(description = "New status (manual; progress never changes it); cannot be null.", example = "doing") Optional<GoalStatus> status,
        @Schema(description = "New period start (date-only); cannot be null.", example = "2026-09-23") Optional<LocalDate> periodStart,
        @Schema(description = "New period end (date-only); cannot be null; not before periodStart.", example = "2026-12-21") Optional<LocalDate> periodEnd,
        @Schema(description = "New deadline (date-only); null clears it.", example = "2026-12-15") Optional<LocalDate> deadline,
        @Schema(description = "New parent (strictly coarser, not archived, no cycle); null makes the goal standalone.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") Optional<String> parentGoalId) {
}
