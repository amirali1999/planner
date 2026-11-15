package com.lifeplanner.model.srv;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;

@Schema(description = "A task put on a week without a day; task is the live task (same data as /tasks).")
public record WeeklyPlanItemSrv(
        @Schema(description = "Weekly plan item id.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String id,
        @Schema(description = "Saturday the week starts on (date-only).", example = "2026-11-07") LocalDate weekStart,
        @Schema(description = "When the task was put on the week (UTC).", example = "2026-11-12T08:30:00.000Z") Instant addedAt,
        @Schema(description = "The task (live; may be done or archived).") TaskSrv task) {
}
