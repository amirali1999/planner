package com.lifeplanner.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Account creation. Format rules (email shape, password length in bytes) are checked in {@code AuthService}. */
@Schema(description = "New account. The first account created on a database also receives all data that existed before authentication.")
public record RegisterVO(
        @NotBlank(message = "required") @Size(max = 254, message = "too_long") @Schema(description = "E-mail, stored trimmed and lower-case; must be unique.", example = "ali@example.com") String email,
        @NotBlank(message = "required") @Size(max = 128, message = "too_long") @Schema(description = "Password: at least 8 characters and at most 72 bytes in UTF-8 (BCrypt limit).", example = "correct horse battery", format = "password") String password,
        @Size(max = 100, message = "too_long") @Schema(description = "Optional name shown in the UI, at most 100 characters.", example = "علی") String displayName) {

    /** Never print the password (e.g. in logs). */
    @Override
    public String toString() {
        return "RegisterVO[email=" + email + ", displayName=" + displayName + "]";
    }
}
