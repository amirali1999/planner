package com.lifeplanner.model.srv;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** Today's plan summary; completionPercent is null with no plan or no countable task. */
@Schema(description = "Summary of the daily plan of the requested date.")
public record TodaySummarySrv(
        @Schema(description = "Whether a plan is stored for the date.", example = "true") boolean planExists,
        @Schema(description = "Number of planned items (including archived tasks).", example = "5") int plannedCount,
        @Schema(description = "Planned tasks that are not archived.", example = "4") int activeTaskCount,
        @Schema(description = "Of those, tasks with status done.", example = "3") int doneTaskCount,
        @Schema(description = "done ÷ active × 100 (2 decimals); null without a plan or without active tasks (not 0).", example = "75") BigDecimal completionPercent) {
}
