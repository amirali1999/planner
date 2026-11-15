package com.lifeplanner.controller;

import com.lifeplanner.model.srv.FocusItemNoteSrv;
import com.lifeplanner.model.srv.NoteCountSrv;
import com.lifeplanner.model.vo.FocusItemNoteVO;
import com.lifeplanner.service.FocusItemNoteService;
import com.lifeplanner.util.ApiPaths;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.net.URI;
import java.util.List;

@Tag(name = "Focus item notes")
@RestController
public class FocusItemNoteController {

    private final FocusItemNoteService service;

    public FocusItemNoteController(FocusItemNoteService service) {
        this.service = service;
    }

    @Operation(summary = "Notes of a focus item", description = "Read-only. The item's notes, newest first (also for an archived item).")
    @ApiResponse(responseCode = "200", description = "The notes (possibly empty).")
    @ApiResponse(responseCode = "404", description = "Focus item not found (or another user's).")
    @GetMapping(ApiPaths.V1 + "/focus-items/{focusItemId}/notes")
    public List<FocusItemNoteSrv> list(@Parameter(description = "Id of one of your focus items.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b11") @PathVariable String focusItemId) {
        return service.list(focusItemId);
    }

    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a note to a focus item", description = "Plain text kept with the item; a note is not a task (no status, planning or progress).")
    @ApiResponse(responseCode = "201", description = "The new note.", headers = {@Header(name = "Location", description = "URL of the created resource.", schema = @Schema(type = "string"))})
    @ApiResponse(responseCode = "404", description = "Focus item not found (or another user's).")
    @ApiResponse(responseCode = "409", description = "focus_item_archived.")
    @PostMapping(ApiPaths.V1 + "/focus-items/{focusItemId}/notes")
    public ResponseEntity<FocusItemNoteSrv> create(@Parameter(description = "Id of one of your focus items.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b11") @PathVariable String focusItemId,
                                                   @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The note text.", required = true) @Valid @RequestBody FocusItemNoteVO request) {
        FocusItemNoteSrv note = service.create(focusItemId, request);
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/focus-item-notes/" + note.id())).body(note);
    }

    @Operation(summary = "Note counts per focus item", description = "Read-only. How many notes each of your focus items has (items without notes are omitted).")
    @ApiResponse(responseCode = "200", description = "The counts (possibly empty).")
    @GetMapping(ApiPaths.V1 + "/focus-item-notes/counts")
    public List<NoteCountSrv> counts() {
        return service.counts();
    }

    @Operation(summary = "Edit a note", description = "Replaces the note's text.")
    @ApiResponse(responseCode = "200", description = "The updated note.")
    @ApiResponse(responseCode = "404", description = "Note not found (or another user's).")
    @ApiResponse(responseCode = "409", description = "focus_item_archived.")
    @PutMapping(ApiPaths.V1 + "/focus-item-notes/{noteId}")
    public FocusItemNoteSrv update(@Parameter(description = "Id of one of your notes.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String noteId,
                                   @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The new text.", required = true) @Valid @RequestBody FocusItemNoteVO request) {
        return service.update(noteId, request);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a note", description = "Removes a note (free text, not history). The focus item is not changed.")
    @ApiResponse(responseCode = "204", description = "Deleted.")
    @ApiResponse(responseCode = "404", description = "Note not found (or another user's).")
    @ApiResponse(responseCode = "409", description = "focus_item_archived.")
    @DeleteMapping(ApiPaths.V1 + "/focus-item-notes/{noteId}")
    public void delete(@Parameter(description = "Id of one of your notes.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") @PathVariable String noteId) {
        service.delete(noteId);
    }
}
