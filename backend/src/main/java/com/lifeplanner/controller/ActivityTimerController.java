package com.lifeplanner.controller;
import com.lifeplanner.model.srv.ActivityLogSrv;
import com.lifeplanner.model.srv.ActivityTimerSrv;
import com.lifeplanner.model.vo.StartActivityTimerVO;
import com.lifeplanner.service.ActivityTimerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.List;
@RestController
@RequestMapping("/api/v1/activity-timer")
@Tag(name="Activity timer")
public class ActivityTimerController {
 private final ActivityTimerService service;
 public ActivityTimerController(ActivityTimerService service) { this.service=service; }
 @GetMapping
 @Operation(summary="Read running timer",description="Read-only, current user's persisted running timer or null.")
 @ApiResponse(responseCode="200",description="Running timer or null.")
 public ActivityTimerSrv get() { return service.get(); }
 @PostMapping
 @Operation(summary="Start activity timer",description="Captures server time and timezone without creating completed log intervals. Optional plannedMinutes marks a Pomodoro and caps its final logged duration.")
 @ApiResponse(responseCode="200",description="Started timer.")
 @ApiResponse(responseCode="409",description="timer_running or activity_log_overlap.")
 public ActivityTimerSrv start(@io.swagger.v3.oas.annotations.parameters.RequestBody(description="Activity title, timezone and optional Pomodoro duration.",required=true) @Valid @RequestBody StartActivityTimerVO request) { return service.start(request); }
 @PostMapping("/stop")
 @Operation(summary="Stop activity timer",description="Atomically creates minute-precision actual logs, split at local midnight, then removes the running timer. Pomodoro logs are capped at plannedMinutes; an early stop records only elapsed time. An open-ended timer stops at the first pre-existing log and records only its free prefix. Minimum one minute for an immediate stop. A legacy or concurrent Pomodoro overlap preserves the timer so it can be corrected or cancelled. Repeated stop returns an empty list.")
 @ApiResponse(responseCode="200",description="Completed intervals; empty when already stopped.")
 @ApiResponse(responseCode="409",description="activity_log_overlap (legacy/concurrent Pomodoro) or clock_changed; running timer is preserved.")
 public List<ActivityLogSrv> stop() { return service.stop(); }
 @DeleteMapping
 @ResponseStatus(HttpStatus.NO_CONTENT)
 @Operation(summary="Cancel activity timer",description="Explicitly discards the current running timer without writing an activity log. Repeated cancellation is a no-op.")
 @ApiResponse(responseCode="204",description="Timer discarded or already absent.")
 public void cancel() { service.cancel(); }
}
