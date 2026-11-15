package com.lifeplanner.model.srv;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** A plan entry with the full, live Task (same record as in /tasks; never a copy). */
@Schema(description = "A task planned on the day; task is the live task (same data as /tasks).")
public record DailyPlanItemSrv(@Schema(description = "Plan item id.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String id, @Schema(description = "Order within the day, from 0.", example = "0") int position, @Schema(description = "When the task was added to the day (UTC).", example = "2026-11-09T08:30:00.000Z") Instant addedAt, @Schema(description = "The planned task.") TaskSrv task) {
}
