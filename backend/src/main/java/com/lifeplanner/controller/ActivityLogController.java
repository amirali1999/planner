package com.lifeplanner.controller;

import com.lifeplanner.model.srv.ActivityLogSrv;
import com.lifeplanner.model.vo.CreateActivityLogVO;
import com.lifeplanner.model.vo.UpdateActivityLogVO;
import com.lifeplanner.service.ActivityLogService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.net.URI;
import java.util.List;

@Tag(name = "Activity logs")
@RestController
@RequestMapping(ApiPaths.V1 + "/activity-logs")
public class ActivityLogController {

    private final ActivityLogService service;

    public ActivityLogController(ActivityLogService service) {
        this.service = service;
    }

    @Operation(summary = "Actual activities of one day", description = "Read-only. Lists what you recorded as actually done on the given local day, ordered by start time. This is separate from tasks, daily plans and time blocks.")
    @ApiResponse(responseCode = "200", description = "The day's activities (possibly empty).")
    @GetMapping
    public List<ActivityLogSrv> list(@Parameter(description = "Local day, Gregorian ISO YYYY-MM-DD.", example = "2026-11-14", schema = @Schema(type = "string", format = "date")) @RequestParam String date) {
        return service.list(IsoDates.parseParam("date", date));
    }

    @Operation(summary = "Record an actual activity", description = "Records what you actually did in a closed local-time interval. It never changes a task or plan. Intervals of the same day may touch but cannot overlap.")
    @ApiResponse(responseCode = "201", description = "The recorded activity.", headers = {@Header(name = "Location", description = "URL of the created resource.", schema = @Schema(type = "string"))})
    @ApiResponse(responseCode = "409", description = "activity_log_overlap (details.activityLogId identifies the other activity) or activity_timer_overlap.")
    @PostMapping
    public ResponseEntity<ActivityLogSrv> create(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The actual activity and its local-time interval.", required = true) @Valid @RequestBody CreateActivityLogVO request) {
        ActivityLogSrv created = service.create(request);
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/activity-logs/" + created.id())).body(created);
    }

    @Operation(summary = "Replace an actual activity", description = "Edits all user-controlled fields of one of your activity logs. Intervals may touch but cannot overlap.")
    @ApiResponse(responseCode = "200", description = "The updated activity.")
    @ApiResponse(responseCode = "404", description = "Activity log not found (or another user's).")
    @ApiResponse(responseCode = "409", description = "activity_log_overlap or activity_timer_overlap.")
    @PutMapping("/{activityLogId}")
    public ActivityLogSrv update(@Parameter(description = "Id of one of your activity logs.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String activityLogId,
                                 @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Full replacement of the editable fields.", required = true) @Valid @RequestBody UpdateActivityLogVO request) {
        return service.update(activityLogId, request);
    }

    @Operation(summary = "Delete an actual activity", description = "Permanently removes the selected activity log after an explicit user action; tasks and plans are unaffected.")
    @ApiResponse(responseCode = "204", description = "Deleted.")
    @ApiResponse(responseCode = "404", description = "Activity log not found (or another user's).")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{activityLogId}")
    public void delete(@Parameter(description = "Id of one of your activity logs.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String activityLogId) {
        service.delete(activityLogId);
    }
}
