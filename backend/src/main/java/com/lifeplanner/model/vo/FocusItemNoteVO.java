package com.lifeplanner.model.vo;

import com.lifeplanner.model.NoteLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Text of a focus-item note (create or replace).")
public record FocusItemNoteVO(
        @NotBlank(message = "required") @Size(max = NoteLimits.BODY_MAX, message = "too_long")
        @Schema(description = "Plain text, at most 4000 characters.", example = "فصل ۳ را دوباره بخوانم؛ مثال‌های RAG مهم‌اند.") String body) {
}
