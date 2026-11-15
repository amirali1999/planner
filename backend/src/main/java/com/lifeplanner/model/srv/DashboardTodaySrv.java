package com.lifeplanner.model.srv;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Dashboard for one date: today's plan summary and the goals active on that date.")
public record DashboardTodaySrv(@Schema(description = "The date asked for (the client's local date).", example = "2026-11-09") LocalDate date, @Schema(description = "Summary of the daily plan of that date.") TodaySummarySrv today, @Schema(description = "Goals active on the date: not archived, status not done/dropped, period containing the date.") List<GoalCardSrv> goals) {
}
