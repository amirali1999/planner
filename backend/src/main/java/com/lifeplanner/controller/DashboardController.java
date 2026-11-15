package com.lifeplanner.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import com.lifeplanner.model.enums.ArchiveFilter;
import com.lifeplanner.model.srv.DashboardTodaySrv;
import com.lifeplanner.model.srv.GoalProgressSrv;
import com.lifeplanner.model.srv.GoalTreeSrv;
import com.lifeplanner.service.DashboardService;
import com.lifeplanner.service.GoalTreeService;
import com.lifeplanner.service.ProgressService;
import com.lifeplanner.util.ApiPaths;
import com.lifeplanner.util.IsoDates;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only progress endpoints. Both measurements are returned side by side, never combined. */
@Tag(name = "Progress & tree")
@RestController
@RequestMapping(ApiPaths.V1)
public class DashboardController {

    private final ProgressService progress;
    private final DashboardService dashboard;
    private final GoalTreeService tree;

    public DashboardController(ProgressService progress, DashboardService dashboard, GoalTreeService tree) {
        this.progress = progress;
        this.dashboard = dashboard;
        this.tree = tree;
    }

    /**
     * Goal forest: independent goals and goals whose parent is filtered out are roots; every goal appears once.
     * {@code archived=false} (default) | {@code true} | {@code all}. Literal path wins over /goals/{goalId}.
     */
    @Operation(summary = "Goal tree", description = "All your goals as a forest. Standalone goals and goals whose parent is excluded by the archive filter are roots (detachedFromParent=true); every goal appears exactly once; corrupt cycles are tolerated. Each node carries its own Outcome and Execution.")
    @ApiResponse(responseCode = "200", description = "The forest.")
    @GetMapping("/goals/tree")
    public GoalTreeSrv goalTree(@Parameter(description = "Archive filter: false = active only (default), true = archived only, all = both. Archived rows are only ever filtered, never removed.", example = "false", schema = @Schema(type = "string", allowableValues = {"false", "true", "all"}, defaultValue = "false")) @RequestParam(defaultValue = "false") String archived) {
        return tree.tree(ArchiveFilter.fromParam(archived), archived);
    }

    /**
     * Progress of up to 200 goals at once ({@code ids} repeatable or comma-separated), in request order. Same values
     * as {@code /goals/{id}/progress}. Literal path wins over /goals/{goalId}.
     */
    @Operation(summary = "Progress of several goals", description = "Outcome and Execution of up to 200 goals in one request, in request order (duplicates removed). Same values as GET /goals/{goalId}/progress.")
    @ApiResponse(responseCode = "200", description = "One entry per requested goal.")
    @ApiResponse(responseCode = "404", description = "One of the ids is not found (or another user's); details.goalId names it.")
    @GetMapping("/goals/progress")
    public java.util.List<GoalProgressSrv> goalsProgress(@Parameter(description = "Goal ids, 1–200; repeat the parameter or comma-separate.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10,7c1a2b3c-4d5e-4f60-8a9b-0c1d2e3f4a5b") @RequestParam(required = false) java.util.List<String> ids) {
        return progress.batch(ids);
    }

    @Operation(summary = "Progress of a goal", description = "Outcome (from Key Results: own active KRs weighted, else equal mean of direct active children, else null) and Execution (distinct active tasks of the active subtree, each counted once) — two separate numbers, never averaged. Includes the per-KR breakdown and the children's Outcomes. Read-only.")
    @ApiResponse(responseCode = "200", description = "The goal's progress.")
    @ApiResponse(responseCode = "404", description = "Goal not found (or it belongs to another user).")
    @GetMapping("/goals/{goalId}/progress")
    public GoalProgressSrv goalProgress(@Parameter(description = "Id of one of your goals.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String goalId) {
        return progress.forGoal(goalId);
    }

    /** {@code date} is the client's local calendar date (Gregorian ISO). */
    @Operation(summary = "Dashboard of a day", description = "Summary of the daily plan of the given date and the goals active on it (not archived, not done/dropped, period containing the date) with separate Outcome/Execution. Read-only: never creates a plan.")
    @ApiResponse(responseCode = "200", description = "The dashboard.")
    @GetMapping("/dashboard/today")
    public DashboardTodaySrv today(@Parameter(description = "Calendar date, Gregorian ISO YYYY-MM-DD (the client's local date; no time zone conversion).", example = "2026-11-09", schema = @Schema(type = "string", format = "date")) @RequestParam String date) {
        return dashboard.today(IsoDates.parseParam("date", date));
    }
}
