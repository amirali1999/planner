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
import com.lifeplanner.exception.ApiException;
import com.lifeplanner.model.enums.ArchiveFilter;
import com.lifeplanner.model.enums.TaskStatus;
import com.lifeplanner.model.srv.TaskSrv;
import com.lifeplanner.service.TaskService;
import com.lifeplanner.util.ApiPaths;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

/** GET /goals/{goalId}/tasks: the goal's effectively attributed tasks, each exactly once. */
@Tag(name = "Tasks")
@RestController
public class GoalTasksController {

    private final TaskService service;

    public GoalTasksController(TaskService service) {
        this.service = service;
    }

    @Operation(summary = "Tasks of a goal (effective attribution)", description = "Distinct tasks whose effective goal is this goal: linked directly, or through a focus item of this goal when the task has no direct goal (the direct link wins). Each task appears once. Children's tasks are not included (they count in Execution only).")
    @ApiResponse(responseCode = "200", description = "The goal's tasks.")
    @ApiResponse(responseCode = "404", description = "Goal not found (or it belongs to another user).")
    @GetMapping(ApiPaths.V1 + "/goals/{goalId}/tasks")
    public List<TaskSrv> tasks(@Parameter(description = "Id of one of your goals.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String goalId,
                                    @Parameter(description = "Status filter.", example = "doing", schema = @Schema(type = "string", allowableValues = {"not_started", "doing", "done"})) @RequestParam(required = false) String status,
                                    @Parameter(description = "Archive filter: false = active only (default), true = archived only, all = both. Archived rows are only ever filtered, never removed.", example = "false", schema = @Schema(type = "string", allowableValues = {"false", "true", "all"}, defaultValue = "false")) @RequestParam(defaultValue = "false") String archived) {
        TaskStatus statusFilter = null;
        if (status != null) {
            try {
                statusFilter = TaskStatus.fromCode(status);
            } catch (IllegalArgumentException e) {
                throw new ApiException.ValidationException("پارامتر جست‌وجو معتبر نیست.", Map.of("status", "invalid_value"));
            }
        }
        return service.listForGoal(goalId, statusFilter, ArchiveFilter.fromParam(archived)).stream().map(TaskSrv::from).toList();
    }
}
