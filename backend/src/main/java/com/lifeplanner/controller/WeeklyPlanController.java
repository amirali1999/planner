package com.lifeplanner.controller;

import com.lifeplanner.model.srv.WeeklyPlanItemSrv;
import com.lifeplanner.model.vo.AddPlanItemVO;
import com.lifeplanner.service.WeeklyPlanService;
import com.lifeplanner.util.ApiPaths;
import com.lifeplanner.util.IsoDates;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@Tag(name = "Weekly plans")
@RestController
@RequestMapping(ApiPaths.V1 + "/plans/weekly")
public class WeeklyPlanController {

    private final WeeklyPlanService service;

    public WeeklyPlanController(WeeklyPlanService service) {
        this.service = service;
    }

    @Operation(summary = "Tasks put on a week", description = "Read-only: the tasks put on the Saturday–Friday week without a day, oldest first, each with the live task (done and archived tasks included; the UI filters).")
    @ApiResponse(responseCode = "200", description = "The week's items (possibly empty).")
    @GetMapping("/{weekStart}/items")
    public List<WeeklyPlanItemSrv> list(@Parameter(description = "Saturday the week starts on, Gregorian ISO YYYY-MM-DD (the client's local date); any other weekday is 400.", example = "2026-11-07", schema = @Schema(type = "string", format = "date")) @PathVariable String weekStart) {
        return service.list(IsoDates.parseParam("weekStart", weekStart));
    }

    @Operation(summary = "Put a task on a week", description = "Adds an existing task to the week without choosing a day (it then shows on the weekly page). A task can be on a week only once. Planning a day is still done with POST /plans/daily/{date}/items.")
    @ApiResponse(responseCode = "201", description = "The new weekly plan item.", headers = {@Header(name = "Location", description = "URL of the created resource.", schema = @Schema(type = "string"))})
    @ApiResponse(responseCode = "404", description = "Task not found (or another user's).")
    @ApiResponse(responseCode = "409", description = "duplicate_plan_task (already on that week) or task_archived.")
    @PostMapping("/{weekStart}/items")
    public ResponseEntity<WeeklyPlanItemSrv> add(@Parameter(description = "Saturday the week starts on, Gregorian ISO YYYY-MM-DD (the client's local date); any other weekday is 400.", example = "2026-11-07", schema = @Schema(type = "string", format = "date")) @PathVariable String weekStart,
                                                 @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The task to put on the week.", required = true) @Valid @RequestBody AddPlanItemVO request) {
        LocalDate week = IsoDates.parseParam("weekStart", weekStart);
        WeeklyPlanItemSrv item = service.addTask(week, request.taskId().trim());
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/plans/weekly/" + week + "/items")).body(item);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a task from a week",
            description = "Removes the task from every daily plan in this Saturday–Friday week and from its optional undated weekly list, in one transaction. The task itself, its due date and status are unchanged.")
    @ApiResponse(responseCode = "204", description = "The task's planning entries were removed from the week.")
    @ApiResponse(responseCode = "404", description = "Task not found (or another user's), or the task is not planned in this week.")
    @DeleteMapping("/{weekStart}/tasks/{taskId}")
    public void remove(@Parameter(description = "Saturday the week starts on, Gregorian ISO YYYY-MM-DD; any other weekday is 400.", example = "2026-11-07", schema = @Schema(type = "string", format = "date")) @PathVariable String weekStart,
                       @Parameter(description = "Id of the task to take out of this week's plan.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String taskId) {
        service.removeTask(IsoDates.parseParam("weekStart", weekStart), taskId);
    }
}
