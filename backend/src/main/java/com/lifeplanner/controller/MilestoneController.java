package com.lifeplanner.controller;

import com.lifeplanner.model.vo.UpdateMilestoneVO;
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
import com.lifeplanner.model.Milestone;
import com.lifeplanner.model.enums.ArchiveFilter;
import com.lifeplanner.model.enums.MilestoneStatusFilter;
import com.lifeplanner.model.srv.MilestoneSrv;
import com.lifeplanner.model.vo.CreateMilestoneVO;
import com.lifeplanner.service.MilestoneService;
import com.lifeplanner.util.ApiPaths;
import com.lifeplanner.util.IsoDates;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Tag(name = "Milestones")
@RestController
@RequestMapping(ApiPaths.V1)
public class MilestoneController {

    private final MilestoneService service;
    private final MilestonePatchReader patchReader;

    public MilestoneController(MilestoneService service, MilestonePatchReader patchReader) {
        this.service = service;
        this.patchReader = patchReader;
    }

    /**
     * {@code status=all|open|completed|overdue|upcoming} (default all); overdue/upcoming need {@code date}, the
     * client's local calendar date (the backend has no notion of the user's timezone).
     * {@code archived=false|true|all} (default false).
     */
    @Operation(summary = "Milestones of a goal", description = "Ordered by due date (undated last), then creation time. overdue/upcoming are computed against the client's date.")
    @ApiResponse(responseCode = "200", description = "Matching milestones.")
    @ApiResponse(responseCode = "404", description = "Goal not found (or it belongs to another user).")
    @GetMapping("/goals/{goalId}/milestones")
    public List<MilestoneSrv> list(@Parameter(description = "Id of one of your goals.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String goalId,
                                        @Parameter(description = "all (default), open, completed, overdue (open, due before date) or upcoming (open, due on/after date). overdue/upcoming require date.", example = "open", schema = @Schema(type = "string", allowableValues = {"all", "open", "completed", "overdue", "upcoming"}, defaultValue = "all")) @RequestParam(defaultValue = "all") String status,
                                        @Parameter(description = "Calendar date, Gregorian ISO YYYY-MM-DD (the client's local date; no time zone conversion). Required for status=overdue|upcoming.", example = "2026-11-09", schema = @Schema(type = "string", format = "date")) @RequestParam(required = false) String date,
                                        @Parameter(description = "Archive filter: false = active only (default), true = archived only, all = both. Archived rows are only ever filtered, never removed.", example = "false", schema = @Schema(type = "string", allowableValues = {"false", "true", "all"}, defaultValue = "false")) @RequestParam(defaultValue = "false") String archived) {
        MilestoneStatusFilter filter;
        try {
            filter = MilestoneStatusFilter.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException.ValidationException(MilestoneService.MSG_INVALID, Map.of("status", "invalid_value"));
        }
        LocalDate today = null;
        if (filter == MilestoneStatusFilter.OVERDUE || filter == MilestoneStatusFilter.UPCOMING) {
            if (date == null) {
                throw new ApiException.ValidationException(MilestoneService.MSG_INVALID, Map.of("date", "required"));
            }
            today = IsoDates.parseParam("date", date);
        }
        return service.list(goalId, filter, today, ArchiveFilter.fromParam(archived)).stream().map(MilestoneSrv::from).toList();
    }

    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a milestone", description = "Adds a delivery checkpoint to the goal. Milestones never affect Outcome or Execution.")
    @ApiResponse(responseCode = "201", description = "Created milestone.", headers = {@Header(name = "Location", description = "URL of the created resource.", schema = @Schema(type = "string"))})
    @ApiResponse(responseCode = "404", description = "Goal not found (or it belongs to another user).")
    @ApiResponse(responseCode = "409", description = "goal_archived.")
    @PostMapping("/goals/{goalId}/milestones")
    public ResponseEntity<MilestoneSrv> create(@Parameter(description = "Id of one of your goals.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String goalId, @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The new milestone.", required = true) @Valid @RequestBody CreateMilestoneVO request) {
        Milestone m = service.create(goalId, request);
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/milestones/" + m.id())).body(MilestoneSrv.from(m));
    }

    /** Partial update incl. {@code completed} and {@code archived}. */
    @Operation(summary = "Update a milestone", description = "Partial update incl. completed (idempotent) and archived. Absent fields stay unchanged; an explicit null clears an optional field; unknown fields or wrong JSON types are 400.")
    @ApiResponse(responseCode = "200", description = "Updated milestone.")
    @ApiResponse(responseCode = "404", description = "Milestone not found (or another user's).")
    @ApiResponse(responseCode = "409", description = "milestone_archived or goal_archived.")
    @PatchMapping("/milestones/{milestoneId}")
    public MilestoneSrv update(@Parameter(description = "Id of one of your milestones.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String milestoneId, @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Fields to change.", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = UpdateMilestoneVO.class))) @RequestBody JsonNode body) {
        return MilestoneSrv.from(service.update(milestoneId, patchReader.read(body)));
    }
}
