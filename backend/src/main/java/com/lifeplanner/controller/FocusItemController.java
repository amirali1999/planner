package com.lifeplanner.controller;

import com.lifeplanner.model.vo.UpdateFocusItemVO;
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
import com.lifeplanner.model.FocusItem;
import com.lifeplanner.model.enums.ArchiveFilter;
import com.lifeplanner.model.enums.FocusKind;
import com.lifeplanner.model.enums.FocusStatus;
import com.lifeplanner.model.srv.FocusItemSrv;
import com.lifeplanner.model.vo.CreateFocusItemVO;
import com.lifeplanner.service.FocusItemService;
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
import java.util.Map;
import java.util.function.Function;

@Tag(name = "Focus items")
@RestController
@RequestMapping(ApiPaths.V1 + "/focus-items")
public class FocusItemController {

    private final FocusItemService service;
    private final FocusItemPatchReader patchReader;

    public FocusItemController(FocusItemService service, FocusItemPatchReader patchReader) {
        this.service = service;
        this.patchReader = patchReader;
    }

    /** Filters: {@code archived=false|true|all} (default false), {@code goalId}, {@code kind}, {@code status}. */
    @Operation(summary = "List focus items", description = "Your focus items in creation order, optionally filtered.")
    @ApiResponse(responseCode = "200", description = "Matching focus items.")
    @GetMapping
    public List<FocusItemSrv> list(@Parameter(description = "Archive filter: false = active only (default), true = archived only, all = both. Archived rows are only ever filtered, never removed.", example = "false", schema = @Schema(type = "string", allowableValues = {"false", "true", "all"}, defaultValue = "false")) @RequestParam(defaultValue = "false") String archived,
                                        @Parameter(description = "Only focus items linked to this goal.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @RequestParam(required = false) String goalId,
                                        @Parameter(description = "Kind filter.", example = "book", schema = @Schema(type = "string", allowableValues = {"project", "book", "course", "area", "other"})) @RequestParam(required = false) String kind,
                                        @Parameter(description = "Status filter.", example = "doing", schema = @Schema(type = "string", allowableValues = {"not_started", "doing", "done"})) @RequestParam(required = false) String status) {
        return service.list(ArchiveFilter.fromParam(archived), goalId, parse("kind", kind, FocusKind::fromCode),
                parse("status", status, FocusStatus::fromCode)).stream().map(FocusItemSrv::from).toList();
    }

    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a focus item", description = "Creates a focus item, optionally linked to one of your active goals.")
    @ApiResponse(responseCode = "201", description = "Created focus item.", headers = {@Header(name = "Location", description = "URL of the created resource.", schema = @Schema(type = "string"))})
    @ApiResponse(responseCode = "404", description = "goalId not found (or another user's).")
    @ApiResponse(responseCode = "409", description = "goal_archived.")
    @PostMapping
    public ResponseEntity<FocusItemSrv> create(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The new focus item.", required = true) @Valid @RequestBody CreateFocusItemVO request) {
        FocusItem item = service.create(request);
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/focus-items/" + item.id())).body(FocusItemSrv.from(item));
    }

    @Operation(summary = "Get a focus item", description = "One focus item, including archived ones.")
    @ApiResponse(responseCode = "200", description = "The focus item.")
    @ApiResponse(responseCode = "404", description = "Focus item not found (or another user's).")
    @GetMapping("/{focusItemId}")
    public FocusItemSrv get(@Parameter(description = "Id of one of your focus items.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String focusItemId) {
        return FocusItemSrv.from(service.get(focusItemId));
    }

    /** Partial update; {@code "archived": true|false} archives/restores (contract: archive via archived flag). */
    @Operation(summary = "Update a focus item", description = "Partial update; archived=true archives, false restores. Absent fields stay unchanged; an explicit null clears an optional field; unknown fields or wrong JSON types are 400. Moving the focus item to another goal moves the attribution of its tasks that have no direct goal.")
    @ApiResponse(responseCode = "200", description = "Updated focus item.")
    @ApiResponse(responseCode = "404", description = "Focus item or goal not found (or another user's).")
    @ApiResponse(responseCode = "409", description = "focus_item_archived (edit of an archived item other than restoring) or goal_archived.")
    @PatchMapping("/{focusItemId}")
    public FocusItemSrv update(@Parameter(description = "Id of one of your focus items.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String focusItemId, @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Fields to change.", required = true, content = @Content(mediaType = "application/json", schema = @Schema(implementation = UpdateFocusItemVO.class))) @RequestBody JsonNode body) {
        return FocusItemSrv.from(service.update(focusItemId, patchReader.read(body)));
    }

    private static <T> T parse(String name, String value, Function<String, T> parser) {
        if (value == null) {
            return null;
        }
        try {
            return parser.apply(value);
        } catch (IllegalArgumentException e) {
            throw new ApiException.ValidationException("پارامتر جست‌وجو معتبر نیست.", Map.of(name, "invalid_value"));
        }
    }
}
