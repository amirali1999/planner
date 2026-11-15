package com.lifeplanner.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Credentials for POST /auth/login.")
public record LoginVO(
        @NotBlank(message = "required") @Size(max = 254, message = "too_long") @Schema(description = "Account e-mail (case-insensitive).", example = "ali@example.com") String email,
        @NotBlank(message = "required") @Size(max = 128, message = "too_long") @Schema(description = "Account password.", example = "correct horse battery", format = "password") String password) {

    @Override
    public String toString() {
        return "LoginVO[email=" + email + "]";
    }
}
