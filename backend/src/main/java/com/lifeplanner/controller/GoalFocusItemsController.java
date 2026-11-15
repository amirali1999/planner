package com.lifeplanner.controller;

import com.lifeplanner.model.srv.FocusItemSrv;
import com.lifeplanner.model.vo.ReorderFocusItemsVO;
import com.lifeplanner.service.FocusItemService;
import com.lifeplanner.util.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** Order of the focus items inside one goal (goals board drag handles). */
@Tag(name = "Focus items")
@RestController
public class GoalFocusItemsController {

    private final FocusItemService service;

    public GoalFocusItemsController(FocusItemService service) {
        this.service = service;
    }

    @Operation(summary = "Set the order of a goal's focus items",
            description = "Replaces the manual order of the focus items of ONE goal: send every active focus item of that goal exactly once, top first. Items of another goal, archived items, duplicates or a partial list are rejected (400), so items never move between goals here (use PATCH /focus-items/{id} with goalId to relink; a relinked item is appended to its new goal). New items are appended at the bottom.")
    @ApiResponse(responseCode = "200", description = "The goal's active focus items in their new order, with their new position.")
    @ApiResponse(responseCode = "404", description = "Goal not found (or it belongs to another user).")
    @PutMapping(ApiPaths.V1 + "/goals/{goalId}/focus-items/order")
    public List<FocusItemSrv> reorder(
            @Parameter(description = "Id of one of your goals.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String goalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The complete new order.", required = true)
            @RequestBody ReorderFocusItemsVO request) {
        return service.reorder(goalId, request.focusItemIds()).stream().map(FocusItemSrv::from).toList();
    }
}
