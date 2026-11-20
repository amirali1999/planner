package com.lifeplanner.model.srv;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Number of notes attached to an active task that has notes.")
public record TaskNoteCountSrv(
        @Schema(description = "Task ID.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b11") String taskId,
        @Schema(description = "Number of notes attached to the task.", example = "3") int count) {
}
