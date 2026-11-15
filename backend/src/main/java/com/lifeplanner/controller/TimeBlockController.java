package com.lifeplanner.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifeplanner.model.srv.TimeBlockSrv;
import com.lifeplanner.model.vo.CreateTimeBlockVO;
import com.lifeplanner.model.vo.UpdateTimeBlockVO;
import com.lifeplanner.service.TimeBlockService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.net.URI;
import java.util.List;

@Tag(name = "Time blocks")
@RestController
@RequestMapping(ApiPaths.V1 + "/time-blocks")
public class TimeBlockController {

    private final TimeBlockService service;
    private final TimeBlockPatchReader patchReader;

    public TimeBlockController(TimeBlockService service, TimeBlockPatchReader patchReader) {
        this.service = service;
        this.patchReader = patchReader;
    }

    @Operation(summary = "Time blocks of a date range", description = "Read-only. Your blocks whose local day lies in from..to (inclusive, at most 42 days), ordered by day and start. Blocks of archived tasks are included (task.archivedAt is set); the UI hides them.")
    @ApiResponse(responseCode = "200", description = "The blocks (possibly empty).")
    @GetMapping
    public List<TimeBlockSrv> list(@Parameter(description = "First local day, Gregorian ISO YYYY-MM-DD.", example = "2026-11-08", schema = @Schema(type = "string", format = "date")) @RequestParam String from,
                                   @Parameter(description = "Last local day (inclusive), not before from; at most 42 days after it.", example = "2026-11-14", schema = @Schema(type = "string", format = "date")) @RequestParam String to) {
        return service.list(IsoDates.parseParam("from", from), IsoDates.parseParam("to", to));
    }

    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a time block", description = "Plans time on one local day, linked to a task and/or with its own title. Minutes are multiples of 5; a block must not overlap another block of the same day. The task itself is not changed.")
    @ApiResponse(responseCode = "201", description = "The new block.", headers = {@Header(name = "Location", description = "URL of the created resource.", schema = @Schema(type = "string"))})
    @ApiResponse(responseCode = "404", description = "Linked task not found (or another user's).")
    @ApiResponse(responseCode = "409", description = "time_block_overlap (details.timeBlockId = the other block) or task_archived.")
    @PostMapping
    public ResponseEntity<TimeBlockSrv> create(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The new block.", required = true) @Valid @RequestBody CreateTimeBlockVO request) {
        TimeBlockSrv block = service.create(request);
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/time-blocks/" + block.id())).body(block);
    }

    @Operation(summary = "Update a time block", description = "Partial update: move (date/startMinute/endMinute), resize, retitle, relink or recolour. Absent fields stay unchanged; null clears title or taskId (one of them must remain). Same rules as create.")
    @ApiResponse(responseCode = "200", description = "The updated block.")
    @ApiResponse(responseCode = "404", description = "Block (or newly linked task) not found, or another user's.")
    @ApiResponse(responseCode = "409", description = "time_block_overlap or task_archived.")
    @PatchMapping("/{timeBlockId}")
    public TimeBlockSrv update(@Parameter(description = "Id of one of your time blocks.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String timeBlockId,
                               @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Fields to change.", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = UpdateTimeBlockVO.class))) @RequestBody JsonNode body) {
        return service.update(timeBlockId, patchReader.read(body));
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a time block", description = "Removes a block of planned time (a planning aid, not history). The linked task is not changed.")
    @ApiResponse(responseCode = "204", description = "Deleted.")
    @ApiResponse(responseCode = "404", description = "Block not found (or another user's).")
    @DeleteMapping("/{timeBlockId}")
    public void delete(@Parameter(description = "Id of one of your time blocks.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String timeBlockId) {
        service.delete(timeBlockId);
    }
}
