package com.lifeplanner.model.srv;

import com.lifeplanner.model.TaskNote;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "A free-text note attached to a task.")
public record TaskNoteSrv(
        @Schema(description = "Note ID.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String id,
        @Schema(description = "ID of the task that owns the note.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b11") String taskId,
        @Schema(description = "Plain text of the note. May contain line breaks.", example = "مثال‌های ویدیو را مرور کنم.") String body,
        @Schema(description = "Creation timestamp in UTC.", example = "2026-11-20T08:30:00.000Z") Instant createdAt,
        @Schema(description = "Last update timestamp in UTC.", example = "2026-11-20T08:30:00.000Z") Instant updatedAt) {

    public static TaskNoteSrv from(TaskNote note) {
        return new TaskNoteSrv(note.id(), note.taskId(), note.body(), note.createdAt(), note.updatedAt());
    }
}
