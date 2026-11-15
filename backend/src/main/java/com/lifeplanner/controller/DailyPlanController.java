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
import com.lifeplanner.model.srv.CarriedTaskSrv;
import com.lifeplanner.model.srv.DailyPlanItemSrv;
import com.lifeplanner.model.srv.DailyPlanSrv;
import com.lifeplanner.model.vo.AddPlanItemVO;
import com.lifeplanner.model.vo.MovePlanTaskVO;
import com.lifeplanner.model.vo.ReorderPlanItemsVO;
import com.lifeplanner.service.DailyPlanService;
import com.lifeplanner.util.ApiPaths;
import com.lifeplanner.util.IsoDates;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "Daily plans")
@RestController
@RequestMapping(ApiPaths.V1 + "/plans/daily")
public class DailyPlanController {

    private final DailyPlanService service;

    public DailyPlanController(DailyPlanService service) {
        this.service = service;
    }

    /** Side-effect free: never creates a plan. The client sends its own local date. */
    @Operation(summary = "Plan of a date", description = "Read-only: a date without a plan returns exists=false and no items (no plan is created).")
    @ApiResponse(responseCode = "200", description = "The plan (possibly empty).")
    @GetMapping
    public DailyPlanSrv get(@Parameter(description = "Calendar date, Gregorian ISO YYYY-MM-DD (the client's local date; no time zone conversion).", example = "2026-11-09", schema = @Schema(type = "string", format = "date")) @RequestParam String date) {
        return service.view(IsoDates.parseParam("date", date));
    }

    /** Idempotent create-or-get: 201 the first time, 200 afterwards, same plan id. */
    @Operation(summary = "Create the plan of a date (idempotent)", description = "Creates the plan if missing (201) or returns the existing one (200); always the same plan id for the date.")
    @ApiResponse(responseCode = "200", description = "The plan already existed.")
    @ApiResponse(responseCode = "201", description = "The plan was created.")
    @PutMapping("/{date}")
    public ResponseEntity<DailyPlanSrv> put(@Parameter(description = "Calendar date, Gregorian ISO YYYY-MM-DD (the client's local date; no time zone conversion).", example = "2026-11-09", schema = @Schema(type = "string", format = "date")) @PathVariable("date") String date) {
        LocalDate day = IsoDates.parseParam("date", date);
        DailyPlanService.Ensured ensured = service.ensure(day);
        return ResponseEntity.status(ensured.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(service.toResponse(ensured.plan()));
    }

    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Plan a task on a date", description = "Adds an existing task to the date (creating the plan if needed). The item references the live task, not a copy. A task can be on a date only once. With addToWeek=true, its undated membership in the containing Saturday–Friday week is established in the same transaction.")
    @ApiResponse(responseCode = "201", description = "The new plan item.", headers = {@Header(name = "Location", description = "URL of the created resource.", schema = @Schema(type = "string"))})
    @ApiResponse(responseCode = "404", description = "Task not found (or another user's).")
    @ApiResponse(responseCode = "409", description = "duplicate_plan_task (already planned on that date) or task_archived.")
    @PostMapping("/{date}/items")
    public ResponseEntity<DailyPlanItemSrv> addItem(@Parameter(description = "Calendar date, Gregorian ISO YYYY-MM-DD (the client's local date; no time zone conversion).", example = "2026-11-09", schema = @Schema(type = "string", format = "date")) @PathVariable("date") String date,
                                                         @Parameter(description = "Also put the task on the containing Saturday–Friday week without choosing another day. Both writes are atomic.", example = "false") @RequestParam(defaultValue = "false") boolean addToWeek,
                                                         @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The task to plan.", required = true) @Valid @RequestBody AddPlanItemVO request) {
        LocalDate day = IsoDates.parseParam("date", date);
        DailyPlanItemSrv item = service.addTask(day, request.taskId().trim(), addToWeek);
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/plans/daily/" + day)).body(item);
    }

    @Operation(summary = "Move a planned task to another date",
            description = "Takes the task off this date's plan and appends it to toDate's plan (created if needed), in one transaction. If it is already planned on toDate it just leaves this date. The task itself (status, due date) is not changed.")
    @ApiResponse(responseCode = "200", description = "The task's item on the target date.")
    @ApiResponse(responseCode = "404", description = "The task is not on this date's plan, or not found (or another user's).")
    @ApiResponse(responseCode = "409", description = "task_archived.")
    @PostMapping("/{date}/tasks/{taskId}/move")
    public DailyPlanItemSrv move(@Parameter(description = "Current date of the item, Gregorian ISO YYYY-MM-DD.", example = "2026-11-12", schema = @Schema(type = "string", format = "date")) @PathVariable("date") String date,
                                 @Parameter(description = "Id of the planned task.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable("taskId") String taskId,
                                 @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The target date.", required = true) @Valid @RequestBody MovePlanTaskVO request) {
        return service.moveTask(IsoDates.parseParam("date", date), taskId, request.toDate());
    }

    @Operation(summary = "Tasks carried over to a date",
            description = "Read-only (nothing is created or moved): non-archived tasks that were planned on an earlier day, are not on this date's plan, and are still unfinished; oldest planned day first. With completedFrom/completedTo (the client's local day as a UTC window, both or neither) tasks finished inside that window are included too, so a finished task appears only on the day it was finished.")
    @ApiResponse(responseCode = "200", description = "The carried-over tasks (possibly empty).")
    @GetMapping("/{date}/carry-over")
    public List<CarriedTaskSrv> carryOver(@Parameter(description = "Calendar date, Gregorian ISO YYYY-MM-DD (the client's local date; no time zone conversion).", example = "2026-11-10", schema = @Schema(type = "string", format = "date")) @PathVariable("date") String date,
                                          @Parameter(description = "Start (inclusive, UTC ISO-8601) of the client's local day; requires completedTo.", example = "2026-11-09T20:30:00Z") @RequestParam(required = false) String completedFrom,
                                          @Parameter(description = "End (exclusive, UTC ISO-8601) of the client's local day; requires completedFrom.", example = "2026-11-10T20:30:00Z") @RequestParam(required = false) String completedTo) {
        return service.carriedOver(IsoDates.parseParam("date", date),
                IsoDates.parseInstantParam("completedFrom", completedFrom), IsoDates.parseInstantParam("completedTo", completedTo));
    }

    @Operation(summary = "Set the order of a date's plan items",
            description = "Replaces the manual order of the items of one daily plan (top first; positions become 0..n-1). Send every item of the plan exactly once, including items whose task is archived; duplicates, foreign ids or a partial list are 400. Never creates a plan: a date without one is 404. Task fields (status, blockedReason) are changed with PATCH /tasks/{taskId}, not here.")
    @ApiResponse(responseCode = "200", description = "The plan with its items in the new order.")
    @ApiResponse(responseCode = "404", description = "The date has no plan.")
    @PutMapping("/{date}/items/order")
    public DailyPlanSrv reorder(@Parameter(description = "Calendar date, Gregorian ISO YYYY-MM-DD (the client's local date; no time zone conversion).", example = "2026-11-09", schema = @Schema(type = "string", format = "date")) @PathVariable("date") String date,
                                @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The complete new order.", required = true) @RequestBody ReorderPlanItemsVO request) {
        return service.reorder(IsoDates.parseParam("date", date), request.itemIds());
    }
}
