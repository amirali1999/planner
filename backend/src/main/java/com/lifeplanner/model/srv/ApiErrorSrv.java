package com.lifeplanner.model.srv;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Error body for every non-2xx API response: {@code {code, message, details?}}.
 * {@code code} is a stable English machine code; {@code message} is Persian and user-facing.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Error body of every non-2xx response.")
public record ApiErrorSrv(@Schema(description = "Stable English machine code, e.g. validation_error, not_found, goal_cycle, email_taken, invalid_token.", example = "validation_error") String code, @Schema(description = "Persian, user-facing message.", example = "داده‌های ارسال‌شده معتبر نیست.") String message, @Schema(description = "Optional: field name → field-level code (required, invalid_value, too_long, …) or extra context (e.g. goalId, retryAfterSeconds).") Map<String, Object> details) {

    public static ApiErrorSrv of(String code, String message) {
        return new ApiErrorSrv(code, message, null);
    }
}
