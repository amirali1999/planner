package com.lifeplanner.model.srv;

import io.swagger.v3.oas.annotations.media.Schema;


/**
 * Successful login/registration/refresh, modelled on the OAuth 2.0 token response (RFC 6749 §5.1) in the API's
 * camelCase: {@code tokenType} is always {@code Bearer}, {@code expiresIn} is the access-token lifetime in seconds
 * (omitted while access tokens do not expire; it is optional in RFC 6749 too).
 * The refresh token is not in the body: it is set as an httpOnly cookie.
 */
@Schema(description = "Successful register/login/refresh (shape of the OAuth 2.0 token response, camelCase). The refresh token is NOT in the body: it is set as the httpOnly cookie planner_refresh.")
public record TokenSrv(@Schema(description = "JWT access token (HS256, typ at+jwt) to send as \"Authorization: Bearer <accessToken>\".", example = "eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJIUzI1NiJ9…") String accessToken, @Schema(description = "Always Bearer.", example = "Bearer", allowableValues = {"Bearer"}) String tokenType,
                       @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) @Schema(description = "Access-token lifetime in seconds. Absent while the server issues non-expiring access tokens (temporary setting planner.auth.access-token-expires=false).", example = "900") Long expiresIn,
                       @Schema(description = "The signed-in account.") UserSrv user) {
}
