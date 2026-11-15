package com.lifeplanner.model.srv;

import io.swagger.v3.oas.annotations.media.Schema;
import com.lifeplanner.model.Goal;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** {@code warnings} is present on single-goal responses (GET by id, POST, PATCH, archive) and omitted in lists. */
@Schema(description = "A goal. Status is the user's own judgement and never follows progress.")
public record GoalSrv(
        @Schema(description = "Goal id.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String id,
        @Schema(description = "Title.", example = "یادگیری RAG") String title,
        @Schema(description = "Description; null when none.") String description,
        @Schema(description = "Status chosen by the user.", example = "doing", allowableValues = {"not_started", "doing", "done", "on_hold", "dropped"}) String status,
        @Schema(description = "Period granularity.", example = "quarterly", allowableValues = {"annual", "quarterly", "monthly", "weekly"}) String periodType,
        @Schema(description = "Period start (date-only, inclusive).", example = "2026-09-23") LocalDate periodStart,
        @Schema(description = "Period end (date-only, inclusive).", example = "2026-12-21") LocalDate periodEnd,
        @Schema(description = "Deadline (date-only); null when none.", example = "2026-12-15") LocalDate deadline,
        @Schema(description = "Parent goal; null for a standalone goal. The parent may be archived.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String parentGoalId,
        @Schema(description = "When archived (UTC); null while active. Archiving never cascades.") Instant archivedAt,
        @Schema(description = "Creation time (UTC).", example = "2026-11-09T08:30:00.000Z") Instant createdAt,
        @Schema(description = "Last change (UTC).", example = "2026-11-09T08:30:00.000Z") Instant updatedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(description = "Non-blocking date warnings; only on single-goal responses (absent in lists).") List<GoalWarningSrv> warnings,
        @Schema(description = "Manual priority among your active goals: 1 = highest (right-most card on the goals board). Set with PUT /goals/priority; new goals get the next number; null for archived goals.", example = "1") Integer priority) {

    public static GoalSrv from(Goal g) {
        return from(g, null);
    }

    public static GoalSrv from(Goal g, List<GoalWarningSrv> warnings) {
        return new GoalSrv(g.id(), g.title(), g.description(), g.status().code(), g.periodType().code(),
                g.periodStart(), g.periodEnd(), g.deadline(), g.parentGoalId(),
                g.archivedAt(), g.createdAt(), g.updatedAt(), warnings, g.priority());
    }
}
