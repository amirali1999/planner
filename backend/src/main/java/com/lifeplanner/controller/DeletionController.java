package com.lifeplanner.controller;

import com.lifeplanner.model.srv.DeletionSrv;
import com.lifeplanner.service.DeletionService;
import com.lifeplanner.util.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Permanent deletion of ARCHIVED goals, focus items and tasks (the «بایگانی» page). */
@Tag(name = "Archive")
@RestController
public class DeletionController {

    private final DeletionService service;

    public DeletionController(DeletionService service) {
        this.service = service;
    }

    @Operation(summary = "Delete an archived goal permanently",
            description = "Only an archived goal can be deleted. Removes the goal with its key results, check-ins, milestones and history, all its focus items, and every task linked to the goal directly or through those focus items (tasks are also removed from daily plans). Child goals are kept: their parent link is removed and recorded in their history. Cannot be undone.")
    @ApiResponse(responseCode = "200", description = "Deleted; counts of what was removed.")
    @ApiResponse(responseCode = "404", description = "Goal not found (or it belongs to another user).")
    @ApiResponse(responseCode = "409", description = "not_archived — archive the goal first.")
    @DeleteMapping(ApiPaths.V1 + "/goals/{goalId}")
    public DeletionSrv deleteGoal(@Parameter(description = "Id of one of your archived goals.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String goalId) {
        return service.deleteGoal(goalId);
    }

    @Operation(summary = "Delete an archived focus item permanently",
            description = "Only an archived focus item can be deleted. Removes the item and all its tasks (also from daily plans). Cannot be undone.")
    @ApiResponse(responseCode = "200", description = "Deleted; counts of what was removed.")
    @ApiResponse(responseCode = "404", description = "Focus item not found (or it belongs to another user).")
    @ApiResponse(responseCode = "409", description = "not_archived — archive the focus item first.")
    @DeleteMapping(ApiPaths.V1 + "/focus-items/{focusItemId}")
    public DeletionSrv deleteFocusItem(@Parameter(description = "Id of one of your archived focus items.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String focusItemId) {
        return service.deleteFocusItem(focusItemId);
    }

    @Operation(summary = "Delete an archived task permanently",
            description = "Only an archived task can be deleted. Removes the task and its entries in daily plans. Cannot be undone.")
    @ApiResponse(responseCode = "200", description = "Deleted; counts of what was removed.")
    @ApiResponse(responseCode = "404", description = "Task not found (or it belongs to another user).")
    @ApiResponse(responseCode = "409", description = "not_archived — archive the task first.")
    @DeleteMapping(ApiPaths.V1 + "/tasks/{taskId}")
    public DeletionSrv deleteTask(@Parameter(description = "Id of one of your archived tasks.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String taskId) {
        return service.deleteTask(taskId);
    }
}
