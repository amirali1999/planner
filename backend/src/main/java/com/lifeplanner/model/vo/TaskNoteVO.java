package com.lifeplanner.model.vo;

import com.lifeplanner.model.NoteLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Text of a task note for creation or replacement.")
public record TaskNoteVO(
        @NotBlank(message = "required") @Size(max = NoteLimits.BODY_MAX, message = "too_long")
        @Schema(description = "Plain text, at most 4000 characters.", example = "مثال‌های ویدیو را مرور کنم.") String body) {
}
