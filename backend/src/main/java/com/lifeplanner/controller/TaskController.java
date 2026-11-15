package com.lifeplanner.controller;

import com.lifeplanner.model.vo.UpdateTaskVO;
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
import com.lifeplanner.exception.ApiException;
import com.lifeplanner.model.Task;
import com.lifeplanner.model.enums.ArchiveFilter;
import com.lifeplanner.model.enums.TaskStatus;
import com.lifeplanner.model.srv.TaskSrv;
import com.lifeplanner.model.vo.CreateTaskVO;
import com.lifeplanner.service.TaskService;
import com.lifeplanner.util.ApiPaths;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.net.URI;
import java.util.List;
import java.util.Map;

@Tag(name = "Tasks")
@RestController
@RequestMapping(ApiPaths.V1 + "/tasks")
public class TaskController {

    private final TaskService service;
    private final TaskPatchReader patchReader;

    public TaskController(TaskService service, TaskPatchReader patchReader) {
        this.service = service;
        this.patchReader = patchReader;
    }

    /**
     * Optional filters: {@code status}, {@code goalId} (direct link), {@code focusItemId}, {@code archived=false|true|all}
     * (default false = active tasks only; archived tasks are never dropped, only filtered).
     */
    @Operation(summary = "List tasks", description = "Your tasks in creation order, optionally filtered. goalId filters by the DIRECT goal link; use GET /goals/{goalId}/tasks for the effective attribution.")
    @ApiResponse(responseCode = "200", description = "Matching tasks.")
    @GetMapping
    public List<TaskSrv> list(@Parameter(description = "Status filter.", example = "doing", schema = @Schema(type = "string", allowableValues = {"not_started", "doing", "done"})) @RequestParam(required = false) String status,
                                   @Parameter(description = "Only tasks directly linked to this goal.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @RequestParam(required = false) String goalId,
                                   @Parameter(description = "Only tasks linked to this focus item.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @RequestParam(required = false) String focusItemId,
                                   @Parameter(description = "Archive filter: false = active only (default), true = archived only, all = both. Archived rows are only ever filtered, never removed.", example = "false", schema = @Schema(type = "string", allowableValues = {"false", "true", "all"}, defaultValue = "false")) @RequestParam(defaultValue = "false") String archived) {
        TaskStatus statusFilter = null;
        if (status != null) {
            try {
                statusFilter = TaskStatus.fromCode(status);
            } catch (IllegalArgumentException e) {
                throw invalidParam("status");
            }
        }
        ArchiveFilter archive = ArchiveFilter.fromParam(archived);
        return service.list(statusFilter, goalId, focusItemId, archive).stream().map(TaskSrv::from).toList();
    }

    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a task", description = "Creates a task, standalone or linked to a goal and/or focus item. If both are set and the focus item belongs to another goal, the direct goal wins and a warning is returned.")
    @ApiResponse(responseCode = "201", description = "Created task.", headers = {@Header(name = "Location", description = "URL of the created resource.", schema = @Schema(type = "string"))})
    @ApiResponse(responseCode = "404", description = "goalId or focusItemId not found (or another user's).")
    @ApiResponse(responseCode = "409", description = "goal_archived or focus_item_archived.")
    @PostMapping
    public ResponseEntity<TaskSrv> create(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The new task.", required = true) @Valid @RequestBody CreateTaskVO request) {
        Task task = service.create(request);
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/tasks/" + task.id())).body(TaskSrv.from(task));
    }

    @Operation(summary = "Get a task", description = "One task, including archived ones, with its derived goal attribution.")
    @ApiResponse(responseCode = "200", description = "The task.")
    @ApiResponse(responseCode = "404", description = "Task not found (or another user's).")
    @GetMapping("/{taskId}")
    public TaskSrv get(@Parameter(description = "Id of one of your tasks.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String taskId) {
        return TaskSrv.from(service.get(taskId));
    }

    @Operation(summary = "Update a task", description = "Partial update incl. status, links and archive flag. Absent fields stay unchanged; an explicit null clears an optional field; unknown fields or wrong JSON types are 400.")
    @ApiResponse(responseCode = "200", description = "Updated task.")
    @ApiResponse(responseCode = "404", description = "Task, goal or focus item not found (or another user's).")
    @ApiResponse(responseCode = "409", description = "task_archived (fields of an archived task change only together with archived=false), goal_archived or focus_item_archived (new links to archived entities are refused).")
    @PatchMapping("/{taskId}")
    public TaskSrv update(@Parameter(description = "Id of one of your tasks.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String taskId, @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Fields to change.", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = UpdateTaskVO.class))) @RequestBody JsonNode body) {
        return TaskSrv.from(service.update(taskId, patchReader.read(body)));
    }

    private static ApiException.ValidationException invalidParam(String name) {
        return new ApiException.ValidationException("پارامتر جست‌وجو معتبر نیست.", Map.of(name, "invalid_value"));
    }
}
