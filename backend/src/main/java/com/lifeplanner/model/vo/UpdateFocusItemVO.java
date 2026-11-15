package com.lifeplanner.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import com.lifeplanner.model.enums.FocusKind;
import com.lifeplanner.model.enums.FocusStatus;
import java.util.Optional;

/** Built by {@link FocusItemPatchReader}: null = absent (unchanged), Optional.empty() = explicit null. */
@Schema(description = "Partial update (PATCH): absent field = unchanged, null = clear (optional fields only), value = set.")
public record UpdateFocusItemVO(
        @Schema(description = "New title, 1–200 characters; cannot be null.", example = "کتاب جدید") Optional<String> title,
        @Schema(description = "New kind; cannot be null.", example = "course") Optional<FocusKind> kind,
        @Schema(description = "New description; null clears it.") Optional<String> description,
        @Schema(description = "New goal (one of your active goals); null unlinks. Tasks follow automatically (attribution is derived).", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") Optional<String> goalId,
        @Schema(description = "New status; cannot be null.", example = "doing") Optional<FocusStatus> status,
        @Schema(description = "true archives, false restores. Archived focus items keep attributing their tasks but accept no new links.", example = "true") Optional<Boolean> archived) {
}
