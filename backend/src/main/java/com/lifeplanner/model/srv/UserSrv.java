package com.lifeplanner.model.srv;

import io.swagger.v3.oas.annotations.media.Schema;
import com.lifeplanner.model.User;
import java.time.Instant;

/** Public account data; never contains the password hash. */
@Schema(description = "Public data of an account (never the password hash).")
public record UserSrv(@Schema(description = "User id (the sub claim of access tokens).", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String id, @Schema(description = "E-mail (lower-case).", example = "ali@example.com") String email, @Schema(description = "Display name; null when none.", example = "علی") String displayName, @Schema(description = "When the account was created (UTC).", example = "2026-11-09T08:30:00.000Z") Instant createdAt) {

    public static UserSrv from(User u) {
        return new UserSrv(u.id(), u.email(), u.displayName(), u.createdAt());
    }
}
