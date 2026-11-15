package com.lifeplanner.model.srv;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Number of notes of one focus item.")
public record NoteCountSrv(
        @Schema(description = "Focus item id.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b11") String focusItemId,
        @Schema(description = "How many notes it has (> 0; items without notes are not listed).", example = "3") int count) {
}
