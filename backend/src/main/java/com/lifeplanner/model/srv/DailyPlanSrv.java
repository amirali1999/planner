package com.lifeplanner.model.srv;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Daily plan view. A date with no stored plan is returned as {@code exists=false} with no items and
 * {@code completionPercent=null}. Completion counts non-archived tasks only; null when there are none.
 */
@Schema(description = "Plan of one calendar date. Reading never creates a plan: a date without a plan is returned with exists=false and no items.")
public record DailyPlanSrv(
        @Schema(description = "The calendar date (date-only).", example = "2026-11-09") LocalDate date,
        @Schema(description = "Plan id; null when no plan exists for the date.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String planId,
        @Schema(description = "Whether a plan is stored for the date.", example = "true") boolean exists,
        @Schema(description = "Planned tasks in order (archived tasks stay visible).") List<DailyPlanItemSrv> items,
        @Schema(description = "Planned tasks that are not archived.", example = "4") int activeTaskCount,
        @Schema(description = "Planned non-archived tasks with status done.", example = "3") int doneTaskCount,
        @Schema(description = "done ÷ active × 100 (2 decimals); null when there is no active planned task (not 0).", example = "75") BigDecimal completionPercent,
        @Schema(description = "When the plan was created (UTC); null when it does not exist.", example = "2026-11-09T08:30:00.000Z") Instant createdAt,
        @Schema(description = "When the plan last changed (UTC); null when it does not exist.", example = "2026-11-09T08:30:00.000Z") Instant updatedAt) {
}
