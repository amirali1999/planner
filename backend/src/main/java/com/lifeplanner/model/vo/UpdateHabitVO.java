package com.lifeplanner.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Optional;

/** Built by {@code HabitPatchReader}: null = absent (unchanged), Optional.empty() = explicit null (rejected). */
@Schema(description = "Partial update (PATCH) of a habit: absent = unchanged.")
public record UpdateHabitVO(
        @Schema(description = "New title; cannot be null or blank.", example = "یادگیری زبان (۱۵ دقیقه)") Optional<String> title,
        @Schema(description = "true archives the habit (hidden, checks kept), false restores it.", example = "true") Optional<Boolean> archived) {
}
