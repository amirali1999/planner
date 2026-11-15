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
 @Operation(summary="Start activity timer",description="Captures server time and timezone, without creating completed log intervals.")
 @ApiResponse(responseCode="200",description="Started timer.")
 @ApiResponse(responseCode="409",description="timer_running.")
 public ActivityTimerSrv start(@io.swagger.v3.oas.annotations.parameters.RequestBody(description="Title and timezone.",required=true) @Valid @RequestBody StartActivityTimerVO request) { return service.start(request); }
 @PostMapping("/stop")
 @Operation(summary="Stop activity timer",description="Atomically creates minute-precision actual logs, split at local midnight, then removes running timer. Minimum one minute for an immediate stop. Overlap preserves the running timer so existing logs can be corrected. Repeated stop returns an empty list.")
 @ApiResponse(responseCode="200",description="Completed intervals; empty when already stopped.")
 @ApiResponse(responseCode="409",description="activity_log_overlap or clock_changed; running timer is preserved.")
 public List<ActivityLogSrv> stop() { return service.stop(); }
}
