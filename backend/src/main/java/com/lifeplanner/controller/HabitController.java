package com.lifeplanner.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifeplanner.model.srv.HabitSrv;
import com.lifeplanner.model.vo.CreateHabitVO;
import com.lifeplanner.model.vo.UpdateHabitVO;
import com.lifeplanner.service.HabitService;
import com.lifeplanner.util.ApiPaths;
import com.lifeplanner.util.IsoDates;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.net.URI;
import java.util.List;

@Tag(name = "Habits")
@RestController
@RequestMapping(ApiPaths.V1 + "/habits")
public class HabitController {

    private final HabitService service;
    private final HabitPatchReader patchReader;

    public HabitController(HabitService service, HabitPatchReader patchReader) {
        this.service = service;
        this.patchReader = patchReader;
    }

    @Operation(summary = "Habits with their done days", description = "Read-only. Your active (or, with archived=true, archived) habits in creation order, each with the days in from..to (inclusive, at most 62 days) on which it was done.")
    @ApiResponse(responseCode = "200", description = "The habits (possibly empty).")
    @GetMapping
    public List<HabitSrv> list(@Parameter(description = "First local day, Gregorian ISO YYYY-MM-DD.", example = "2026-11-07", schema = @Schema(type = "string", format = "date")) @RequestParam String from,
                               @Parameter(description = "Last local day (inclusive), not before from; at most 62 days after it.", example = "2026-11-13", schema = @Schema(type = "string", format = "date")) @RequestParam String to,
                               @Parameter(description = "false (default) = active habits, true = archived ones.", example = "false") @RequestParam(defaultValue = "false") boolean archived) {
        return service.list(IsoDates.parseParam("from", from), IsoDates.parseParam("to", to), archived);
    }

    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a habit", description = "A new daily habit with no done days.")
    @ApiResponse(responseCode = "201", description = "The new habit.", headers = {@Header(name = "Location", description = "URL of the created resource.", schema = @Schema(type = "string"))})
    @PostMapping
    public ResponseEntity<HabitSrv> create(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The new habit.", required = true) @Valid @RequestBody CreateHabitVO request) {
        HabitSrv habit = service.create(request);
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/habits/" + habit.id())).body(habit);
    }

    @Operation(summary = "Rename, archive or restore a habit", description = "Partial update. archived=true hides the habit and keeps its done days; archived=false restores it. doneDates in the response is empty (ask GET with a range).")
    @ApiResponse(responseCode = "200", description = "The updated habit.")
    @ApiResponse(responseCode = "404", description = "Habit not found (or another user's).")
    @PatchMapping("/{habitId}")
    public HabitSrv update(@Parameter(description = "Id of one of your habits.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String habitId,
                           @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Fields to change.", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = UpdateHabitVO.class))) @RequestBody JsonNode body) {
        return service.update(habitId, patchReader.read(body));
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Mark a day done", description = "Idempotent: the habit was done on this local day.")
    @ApiResponse(responseCode = "204", description = "Marked.")
    @ApiResponse(responseCode = "404", description = "Habit not found (or another user's).")
    @ApiResponse(responseCode = "409", description = "habit_archived.")
    @PutMapping("/{habitId}/checks/{date}")
    public void check(@Parameter(description = "Id of one of your habits.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String habitId,
                      @Parameter(description = "Local day, Gregorian ISO YYYY-MM-DD.", example = "2026-11-12", schema = @Schema(type = "string", format = "date")) @PathVariable String date) {
        service.setDone(habitId, IsoDates.parseParam("date", date), true);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Unmark a day", description = "Idempotent: the habit was not done on this local day.")
    @ApiResponse(responseCode = "204", description = "Unmarked.")
    @ApiResponse(responseCode = "404", description = "Habit not found (or another user's).")
    @ApiResponse(responseCode = "409", description = "habit_archived.")
    @DeleteMapping("/{habitId}/checks/{date}")
    public void uncheck(@Parameter(description = "Id of one of your habits.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String habitId,
                        @Parameter(description = "Local day, Gregorian ISO YYYY-MM-DD.", example = "2026-11-12", schema = @Schema(type = "string", format = "date")) @PathVariable String date) {
        service.setDone(habitId, IsoDates.parseParam("date", date), false);
    }
}
