package com.lifeplanner.model.vo;

import com.lifeplanner.model.enums.TimeBlockColor;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.Optional;

/** Built by {@code TimeBlockPatchReader}: null = absent (unchanged), Optional.empty() = explicit null. */
@Schema(description = "Partial update (PATCH) of a time block: absent = unchanged, null = clear (title, taskId), value = set. Moving or resizing sends date/startMinute/endMinute.")
public record UpdateTimeBlockVO(
        @Schema(description = "New local day; cannot be null.", example = "2026-11-15") Optional<LocalDate> date,
        @Schema(description = "New start (minutes after local midnight, multiple of 5); cannot be null.", example = "600") Optional<Integer> startMinute,
        @Schema(description = "New end (exclusive; multiple of 5, after start); cannot be null.", example = "690") Optional<Integer> endMinute,
        @Schema(description = "Own title; null clears it (only allowed while a task is linked).", example = "جلسه تیم") Optional<String> title,
        @Schema(description = "Linked task; null unlinks it (then a title is required).", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") Optional<String> taskId,
        @Schema(description = "Colour key; cannot be null.", example = "purple", allowableValues = {"teal", "blue", "purple", "green", "olive", "amber", "rose", "slate"}) Optional<TimeBlockColor> color) {
}
