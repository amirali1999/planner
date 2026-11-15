package com.lifeplanner.controller;

import com.lifeplanner.model.vo.UpdateKeyResultVO;
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
import com.lifeplanner.model.CheckIn;
import com.lifeplanner.model.KeyResult;
import com.lifeplanner.model.srv.CheckInSrv;
import com.lifeplanner.model.srv.KeyResultHistorySrv;
import com.lifeplanner.model.srv.KeyResultSrv;
import com.lifeplanner.model.vo.ArchiveKeyResultVO;
import com.lifeplanner.model.vo.CreateCheckInVO;
import com.lifeplanner.model.vo.CreateKeyResultVO;
import com.lifeplanner.service.KeyResultService;
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

@Tag(name = "Key results")
@RestController
@RequestMapping(ApiPaths.V1)
public class KeyResultController {

    private final KeyResultService service;
    private final KeyResultPatchReader patchReader;

    public KeyResultController(KeyResultService service, KeyResultPatchReader patchReader) {
        this.service = service;
        this.patchReader = patchReader;
    }

    /** name/unit; baseline/target/weight only with an explanatory {@code note} (recalibration audit). */
    @Operation(summary = "Update a Key Result", description = "Partial update. Absent fields stay unchanged; an explicit null clears an optional field; unknown fields or wrong JSON types are 400. Changing baseline, target or weight is a recalibration: note is required and the change is written to the KR history. Check-ins are never changed; current and score are re-derived. KRs of archived goals and archived KRs are read-only.")
    @ApiResponse(responseCode = "200", description = "Updated Key Result.")
    @ApiResponse(responseCode = "404", description = "Key Result not found (or another user's).")
    @ApiResponse(responseCode = "409", description = "key_result_archived or goal_archived.")
    @PatchMapping("/key-results/{keyResultId}")
    public KeyResultSrv update(@Parameter(description = "Id of one of your Key Results.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String keyResultId, @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Fields to change.", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = UpdateKeyResultVO.class))) @RequestBody JsonNode body) {
        return KeyResultSrv.from(service.update(keyResultId, patchReader.read(body)));
    }

    /** Idempotent, 200. Check-ins are kept; the KR stops counting toward Outcome. Optional body {"note": "…"}. */
    @Operation(summary = "Archive a Key Result", description = "The KR stops counting toward the goal's Outcome; its check-ins and history are kept and stay readable. Idempotent.")
    @ApiResponse(responseCode = "200", description = "The archived Key Result.")
    @ApiResponse(responseCode = "404", description = "Key Result not found (or another user's).")
    @ApiResponse(responseCode = "409", description = "goal_archived.")
    @PostMapping("/key-results/{keyResultId}/archive")
    public KeyResultSrv archive(@Parameter(description = "Id of one of your Key Results.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String keyResultId, @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Optional reason for the archive (stored in the history).", required = false) @RequestBody(required = false) ArchiveKeyResultVO body) {
        return KeyResultSrv.from(service.archive(keyResultId, body == null ? null : body.note()));
    }

    @Operation(summary = "Key Result history", description = "Audited changes of the KR (created, changed incl. recalibrations with their note, archived) in chronological order.")
    @ApiResponse(responseCode = "200", description = "History entries, oldest first.")
    @ApiResponse(responseCode = "404", description = "Key Result not found (or another user's).")
    @GetMapping("/key-results/{keyResultId}/history")
    public List<KeyResultHistorySrv> history(@Parameter(description = "Id of one of your Key Results.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String keyResultId) {
        return service.history(keyResultId);
    }

    /** Active KRs of a Goal; {@code includeArchived=true} also returns archived ones (with archivedAt). */
    @Operation(summary = "Key Results of a goal", description = "Active KRs of the goal with derived current value and score, in creation order.")
    @ApiResponse(responseCode = "200", description = "The goal's Key Results.")
    @ApiResponse(responseCode = "404", description = "Goal not found (or it belongs to another user).")
    @GetMapping("/goals/{goalId}/key-results")
    public List<KeyResultSrv> list(@Parameter(description = "Id of one of your goals.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String goalId,
                                        @Parameter(description = "true also returns archived KRs (with archivedAt).", example = "false") @RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.listForGoal(goalId, includeArchived).stream().map(KeyResultSrv::from).toList();
    }

    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a Key Result to a goal", description = "A goal may have any number of KRs. The new KR starts at its baseline with score 0.")
    @ApiResponse(responseCode = "201", description = "Created Key Result.", headers = {@Header(name = "Location", description = "URL of the created resource.", schema = @Schema(type = "string"))})
    @ApiResponse(responseCode = "404", description = "Goal not found (or it belongs to another user).")
    @ApiResponse(responseCode = "409", description = "goal_archived.")
    @PostMapping("/goals/{goalId}/key-results")
    public ResponseEntity<KeyResultSrv> create(@Parameter(description = "Id of one of your goals.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String goalId,
                                                    @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The new Key Result.", required = true) @Valid @RequestBody CreateKeyResultVO request) {
        KeyResult kr = service.create(goalId, request);
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/key-results/" + kr.id()))
                .body(KeyResultSrv.from(kr));
    }

    @Operation(summary = "Get a Key Result", description = "One KR (also archived ones) with its derived current value and score.")
    @ApiResponse(responseCode = "200", description = "The Key Result.")
    @ApiResponse(responseCode = "404", description = "Key Result not found (or another user's).")
    @GetMapping("/key-results/{keyResultId}")
    public KeyResultSrv get(@Parameter(description = "Id of one of your Key Results.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String keyResultId) {
        return KeyResultSrv.from(service.get(keyResultId));
    }

    @Operation(summary = "Check-ins of a Key Result", description = "Full measurement history in chronological order (measuredAt, createdAt, id); the last one drives current.")
    @ApiResponse(responseCode = "200", description = "Check-ins, oldest first.")
    @ApiResponse(responseCode = "404", description = "Key Result not found (or another user's).")
    @GetMapping("/key-results/{keyResultId}/check-ins")
    public List<CheckInSrv> checkIns(@Parameter(description = "Id of one of your Key Results.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String keyResultId) {
        return service.listCheckIns(keyResultId).stream().map(CheckInSrv::from).toList();
    }

    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a check-in", description = "Appends a measurement. Check-ins can never be edited or deleted. Not allowed on archived KRs or KRs of archived goals.")
    @ApiResponse(responseCode = "201", description = "The recorded check-in.", headers = {@Header(name = "Location", description = "URL of the created resource.", schema = @Schema(type = "string"))})
    @ApiResponse(responseCode = "404", description = "Key Result not found (or another user's).")
    @ApiResponse(responseCode = "409", description = "key_result_archived or goal_archived.")
    @PostMapping("/key-results/{keyResultId}/check-ins")
    public ResponseEntity<CheckInSrv> addCheckIn(@Parameter(description = "Id of one of your Key Results.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String keyResultId,
                                                      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The measurement.", required = true) @Valid @RequestBody CreateCheckInVO request) {
        CheckIn checkIn = service.addCheckIn(keyResultId, request);
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/key-results/" + keyResultId + "/check-ins"))
                .body(CheckInSrv.from(checkIn));
    }
}
