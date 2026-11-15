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
import com.lifeplanner.model.InboxItem;
import com.lifeplanner.model.enums.InboxDecision;
import com.lifeplanner.model.enums.InboxStatus;
import com.lifeplanner.model.srv.InboxItemSrv;
import com.lifeplanner.model.srv.ProcessInboxItemSrv;
import com.lifeplanner.model.vo.CaptureInboxItemVO;
import com.lifeplanner.model.vo.ProcessInboxItemVO;
import com.lifeplanner.service.InboxService;
import com.lifeplanner.util.ApiPaths;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Tag(name = "Inbox")
@RestController
@RequestMapping(ApiPaths.V1 + "/inbox-items")
public class InboxController {

    private final InboxService service;

    public InboxController(InboxService service) {
        this.service = service;
    }

    /**
     * {@code status=unprocessed} (default, the queue) | {@code processed} | {@code all}; optional
     * {@code decision=reference} (comma-separated list allowed, e.g. {@code task,goal,focus_item}); newest first.
     */
    @Operation(summary = "List inbox items", description = "Newest first. By default the unprocessed queue.")
    @ApiResponse(responseCode = "200", description = "Matching inbox items.")
    @GetMapping
    public List<InboxItemSrv> list(@Parameter(description = "unprocessed (default, the queue), processed or all.", example = "unprocessed", schema = @Schema(type = "string", allowableValues = {"unprocessed", "processed", "all"}, defaultValue = "unprocessed")) @RequestParam(defaultValue = "unprocessed") String status,
                                        @Parameter(description = "Only processed items with one of these decisions (comma-separated): task, goal, focus_item, reference, someday, discard.", example = "reference") @RequestParam(required = false) String decision) {
        InboxStatus filter = switch (status) {
            case "unprocessed" -> InboxStatus.UNPROCESSED;
            case "processed" -> InboxStatus.PROCESSED;
            case "all" -> null;
            default -> throw new ApiException.ValidationException("پارامتر جست‌وجو معتبر نیست.", Map.of("status", "invalid_value"));
        };
        List<String> decisions = null;
        if (decision != null) {
            try {
                decisions = Arrays.stream(decision.split(",")).map(String::trim).map(d -> InboxDecision.fromCode(d).code()).toList();
            } catch (IllegalArgumentException e) {
                throw new ApiException.ValidationException("پارامتر جست‌وجو معتبر نیست.", Map.of("decision", "invalid_value"));
            }
        }
        return service.list(filter, decisions).stream().map(InboxItemSrv::from).toList();
    }

    /** Body: {"rawText": "…"} only. */
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Capture a note", description = "Stores a raw note as unprocessed. Nothing is inferred or created from the text until it is triaged.")
    @ApiResponse(responseCode = "201", description = "Captured item.", headers = {@Header(name = "Location", description = "URL of the created resource.", schema = @Schema(type = "string"))})
    @PostMapping
    public ResponseEntity<InboxItemSrv> capture(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The note.", required = true) @RequestBody CaptureInboxItemVO request) {
        InboxItem item = service.capture(request.rawText());
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/inbox-items/" + item.id())).body(InboxItemSrv.from(item));
    }

    /**
     * Triage: atomically creates the chosen Task/Goal/Focus Item (or files the item as reference/someday/discard) and
     * marks the item processed with provenance. Already processed → 409 {@code inbox_item_already_processed}.
     */
    @Operation(summary = "Triage an inbox item", description = "Atomically creates the chosen task, goal or focus item (with all the rules of its own create endpoint) or files the item as reference/someday/discard, and marks the item processed with the created entity. On any error nothing is created and the item stays unprocessed.")
    @ApiResponse(responseCode = "200", description = "The item was processed.")
    @ApiResponse(responseCode = "404", description = "Inbox item (or a referenced goal/focus item) not found (or another user's).")
    @ApiResponse(responseCode = "409", description = "inbox_item_already_processed (also for a concurrent triage), or a conflict of the created entity (e.g. goal_archived).")
    @PostMapping("/{inboxItemId}/process")
    public ProcessInboxItemSrv process(@Parameter(description = "Id of one of your inbox items.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String inboxItemId, @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Decision and the payload of that decision.", required = true) @Valid @RequestBody ProcessInboxItemVO request) {
        InboxItem item = service.process(inboxItemId, request);
        return new ProcessInboxItemSrv(item.id(), item.status().code(), item.decision(), item.createdEntityType(),
                item.createdEntityId(), item.processedAt());
    }

    @Operation(summary = "Get an inbox item", description = "One inbox item, processed or not.")
    @ApiResponse(responseCode = "200", description = "The inbox item.")
    @ApiResponse(responseCode = "404", description = "Inbox item not found (or another user's).")
    @GetMapping("/{inboxItemId}")
    public InboxItemSrv get(@Parameter(description = "Id of one of your inbox items.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String inboxItemId) {
        return InboxItemSrv.from(service.get(inboxItemId));
    }
}
