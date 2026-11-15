package com.lifeplanner.model.srv;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** A task carried over to a day's board from an earlier day's plan (it is not on that day's plan itself). */
@Schema(description = "A task planned on an earlier day and not on this day's plan: still unfinished, or finished inside the requested window. Nothing is copied or moved; plannedOn is the latest earlier day it was planned on.")
public record CarriedTaskSrv(
        @Schema(description = "Latest earlier day (date-only) the task was planned on.", example = "2026-11-08") LocalDate plannedOn,
        @Schema(description = "The live task (same data as /tasks).") TaskSrv task) {
}
