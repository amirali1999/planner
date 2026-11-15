package com.lifeplanner.controller;

import com.lifeplanner.model.vo.ReorderGoalsVO;
import com.lifeplanner.model.vo.UpdateGoalVO;
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
import com.lifeplanner.model.Goal;
import com.lifeplanner.model.srv.GoalSrv;
import com.lifeplanner.model.vo.CreateGoalVO;
import com.lifeplanner.model.vo.GoalQueryVO;
import com.lifeplanner.service.GoalService;
import com.lifeplanner.util.ApiPaths;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.net.URI;
import java.util.List;

@Tag(name = "Goals")
@RestController
@RequestMapping(ApiPaths.V1 + "/goals")
public class GoalController {

    private final GoalService service;
    private final GoalPatchReader patchReader;

    public GoalController(GoalService service, GoalPatchReader patchReader) {
        this.service = service;
        this.patchReader = patchReader;
    }

    /**
     * Filtered, stably ordered goal list. The body stays a plain array (existing clients); the number of all matches is
     * in {@code X-Total-Count}. Filters (all optional, combined with AND):
     * {@code archived=false|true|all} (default false), {@code status} and {@code periodType} (repeatable or
     * comma-separated, OR within one parameter; an empty value means no filter), {@code deadlineFrom}/{@code deadlineTo} (inclusive due window),
     * {@code overdueOn} (client's local date), {@code sort=period|deadline}, {@code limit} (1..200, default all),
     * {@code offset} (default 0).
     */
    @Operation(summary = "List goals (filters, sorting, paging)", description = "Your goals in a total, stable order, filtered (all filters optional and combined with AND). The body is a plain array; the number of all matches (ignoring limit/offset) is in X-Total-Count. Goal status is the user's choice and is never derived from progress.")
    @ApiResponse(responseCode = "200", description = "Matching goals (warnings are not included in lists).", headers = {@Header(name = "X-Total-Count", description = "Number of goals matching the filters, regardless of limit/offset.", schema = @Schema(type = "string"))})
    @GetMapping
    public ResponseEntity<List<GoalSrv>> list(@Parameter(description = "Archive filter: false = active only (default), true = archived only, all = both. Archived rows are only ever filtered, never removed.", example = "false", schema = @Schema(type = "string", allowableValues = {"false", "true", "all"}, defaultValue = "false")) @RequestParam(defaultValue = "false") String archived,
                                                   @Parameter(description = "Status filter; repeat the parameter or comma-separate values (OR). Empty = no filter. Values: not_started, doing, done, on_hold, dropped.", example = "doing,on_hold") @RequestParam(required = false) List<String> status,
                                                   @Parameter(description = "Period filter; repeat or comma-separate (OR). Values: annual, quarterly, monthly, weekly.", example = "quarterly") @RequestParam(required = false) List<String> periodType,
                                                   @Parameter(description = "Due window start (inclusive, YYYY-MM-DD). Goals without a deadline never match a window.", example = "2026-11-09", schema = @Schema(type = "string", format = "date")) @RequestParam(required = false) String deadlineFrom,
                                                   @Parameter(description = "Due window end (inclusive, YYYY-MM-DD); not before deadlineFrom.", example = "2026-11-16", schema = @Schema(type = "string", format = "date")) @RequestParam(required = false) String deadlineTo,
                                                   @Parameter(description = "Only overdue goals as of this date (the client's local today, YYYY-MM-DD): deadline before it and status not_started, doing or on_hold (done/dropped are never overdue).", example = "2026-11-09", schema = @Schema(type = "string", format = "date")) @RequestParam(required = false) String overdueOn,
                                                   @Parameter(description = "Order: period (default: period start, coarser period first, creation time, id), deadline (dated goals first by deadline, undated last) or priority (your manual order, 1 first; archived goals last).", example = "period", schema = @Schema(type = "string", allowableValues = {"period", "deadline", "priority"}, defaultValue = "period")) @RequestParam(required = false) String sort,
                                                   @Parameter(description = "Page size 1–200; omit for all matches.", example = "20") @RequestParam(required = false) String limit,
                                                   @Parameter(description = "Number of matches to skip (>= 0, default 0).", example = "0") @RequestParam(required = false) String offset) {
        GoalQueryVO query = GoalQueryVO.parse(archived, status, periodType, deadlineFrom, deadlineTo, overdueOn, sort, limit, offset);
        GoalService.GoalPage page = service.list(query);
        return ResponseEntity.ok()
                .header(TOTAL_COUNT_HEADER, Integer.toString(page.totalCount()))
                .body(page.items().stream().map(GoalSrv::from).toList());
    }

    public static final String TOTAL_COUNT_HEADER = "X-Total-Count";

    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a goal", description = "Creates a goal, standalone or under a parent with a strictly coarser period. Dates outside the parent's period or a deadline outside the goal's period are allowed and come back as warnings.")
    @ApiResponse(responseCode = "201", description = "Created goal (with warnings, if any).", headers = {@Header(name = "Location", description = "URL of the created resource.", schema = @Schema(type = "string"))})
    @ApiResponse(responseCode = "404", description = "parentGoalId not found (or another user's).")
    @ApiResponse(responseCode = "409", description = "Parent not allowed: parent_goal_archived or incompatible_period_granularity.")
    @PostMapping
    public ResponseEntity<GoalSrv> create(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The new goal.", required = true) @Valid @RequestBody CreateGoalVO request) {
        Goal goal = service.create(request);
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/goals/" + goal.id())).body(withWarnings(goal));
    }

    @Operation(summary = "Get a goal", description = "One goal, including non-blocking date warnings. Archived goals stay readable.")
    @ApiResponse(responseCode = "200", description = "The goal.")
    @ApiResponse(responseCode = "404", description = "Goal not found (or it belongs to another user).")
    @GetMapping("/{goalId}")
    public GoalSrv get(@Parameter(description = "Id of one of your goals.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String goalId) {
        return withWarnings(service.get(goalId));
    }

    /** Partial update incl. parentGoalId (null removes the parent). Date mismatches come back as warnings. */
    @Operation(summary = "Update a goal", description = "Partial update of title, description, status, period dates, deadline and parent. Absent fields stay unchanged; an explicit null clears an optional field; unknown fields or wrong JSON types are 400. periodType cannot be changed. Each changed category is recorded in the goal history. Status changes are manual only.")
    @ApiResponse(responseCode = "200", description = "Updated goal (with warnings, if any).")
    @ApiResponse(responseCode = "404", description = "Goal not found (or it belongs to another user).")
    @ApiResponse(responseCode = "409", description = "goal_archived (archived goals are read-only), goal_cycle, parent_goal_archived or incompatible_period_granularity.")
    @PatchMapping("/{goalId}")
    public GoalSrv update(@Parameter(description = "Id of one of your goals.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String goalId, @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Fields to change.", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = UpdateGoalVO.class))) @RequestBody JsonNode body) {
        return withWarnings(service.update(goalId, patchReader.read(body)));
    }

    @Operation(summary = "Set the priority order of goals",
            description = "Replaces the manual priority of all your active goals: send every active goal id exactly once, highest priority first (it becomes priority 1). Used by drag-and-drop on the goals board. Archived goals have no priority; new goals are appended at the end; archiving closes the gap. Changes no other field and writes no goal history.")
    @ApiResponse(responseCode = "200", description = "The active goals in their new order, with their new priority.")
    @PutMapping("/priority")
    public List<GoalSrv> reorder(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The complete new order.", required = true) @RequestBody ReorderGoalsVO request) {
        return service.reorder(request.goalIds()).stream().map(GoalSrv::from).toList();
    }

    /** Idempotent, 200. Never archives children, KRs or tasks. */
    @Operation(summary = "Archive a goal", description = "Hides the goal from active lists. Idempotent. Never archives or deletes children, KRs, check-ins or tasks; archived goals are excluded from their ancestors' progress.")
    @ApiResponse(responseCode = "200", description = "The archived goal.")
    @ApiResponse(responseCode = "404", description = "Goal not found (or it belongs to another user).")
    @PostMapping("/{goalId}/archive")
    public GoalSrv archive(@Parameter(description = "Id of one of your goals.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String goalId) {
        return withWarnings(service.archive(goalId));
    }

    /** Idempotent restore, 200. */
    @Operation(summary = "Restore an archived goal", description = "Makes the goal active again. Idempotent. Its parent link is kept.")
    @ApiResponse(responseCode = "200", description = "The restored goal.")
    @ApiResponse(responseCode = "404", description = "Goal not found (or it belongs to another user).")
    @PostMapping("/{goalId}/unarchive")
    public GoalSrv unarchive(@Parameter(description = "Id of one of your goals.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String goalId) {
        return withWarnings(service.unarchive(goalId));
    }

    private GoalSrv withWarnings(Goal goal) {
        return GoalSrv.from(goal, service.warnings(goal));
    }
}
