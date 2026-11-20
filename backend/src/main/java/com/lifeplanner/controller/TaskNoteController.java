package com.lifeplanner.controller;

import com.lifeplanner.model.srv.TaskNoteCountSrv;
import com.lifeplanner.model.srv.TaskNoteSrv;
import com.lifeplanner.model.vo.TaskNoteVO;
import com.lifeplanner.service.TaskNoteService;
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

@Tag(name = "Task notes")
@RestController
public class TaskNoteController {

    private static final String TASK_NOTES = ApiPaths.V1 + "/tasks/{taskId}/notes";
    private static final String NOTES = ApiPaths.V1 + "/task-notes";

    private final TaskNoteService service;

    public TaskNoteController(TaskNoteService service) {
        this.service = service;
    }

    @Operation(summary = "List the notes of a task", description = "Returns notes newest first. Archived tasks retain readable notes.")
    @ApiResponse(responseCode = "200", description = "The notes, possibly empty.")
    @ApiResponse(responseCode = "404", description = "Task not found or belongs to another user.")
    @GetMapping(TASK_NOTES)
    public List<TaskNoteSrv> list(@Parameter(description = "ID of an owned task.") @PathVariable String taskId) {
        return service.list(taskId);
    }

    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a note to a task", description = "Adds a plain-text note to an active task, including a completed task.")
    @ApiResponse(responseCode = "201", description = "The created note.", headers = @Header(name = "Location", description = "URL of the created note.", schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "404", description = "Task not found or belongs to another user.")
    @ApiResponse(responseCode = "409", description = "task_archived.")
    @PostMapping(TASK_NOTES)
    public ResponseEntity<TaskNoteSrv> create(@Parameter(description = "ID of an owned task.") @PathVariable String taskId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The note text.", required = true) @Valid @RequestBody TaskNoteVO request) {
        TaskNoteSrv note = service.create(taskId, request);
        return ResponseEntity.created(URI.create(NOTES + "/" + note.id())).body(note);
    }

    @Operation(summary = "Count notes by task", description = "Returns one count for each owned active task with at least one note.")
    @ApiResponse(responseCode = "200", description = "The counts, possibly empty.")
    @GetMapping(NOTES + "/counts")
    public List<TaskNoteCountSrv> counts() {
        return service.counts();
    }

    @Operation(summary = "Edit a task note", description = "Replaces the text of an existing note and preserves its creation date.")
    @ApiResponse(responseCode = "200", description = "The updated note.")
    @ApiResponse(responseCode = "404", description = "Note not found or belongs to another user.")
    @ApiResponse(responseCode = "409", description = "task_archived.")
    @PutMapping(NOTES + "/{noteId}")
    public TaskNoteSrv update(@Parameter(description = "ID of an owned task note.") @PathVariable String noteId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "The replacement note text.", required = true) @Valid @RequestBody TaskNoteVO request) {
        return service.update(noteId, request);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a task note", description = "Removes a note from an active task and keeps the task itself.")
    @ApiResponse(responseCode = "204", description = "The note was deleted.")
    @ApiResponse(responseCode = "404", description = "Note not found or belongs to another user.")
    @ApiResponse(responseCode = "409", description = "task_archived.")
    @DeleteMapping(NOTES + "/{noteId}")
    public void delete(@Parameter(description = "ID of an owned task note.") @PathVariable String noteId) {
        service.delete(noteId);
    }
}
