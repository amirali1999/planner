package com.lifeplanner.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import com.lifeplanner.config.PlannerProperties;
import com.lifeplanner.exception.ApiException;
import com.lifeplanner.exception.ErrorCodes;
import com.lifeplanner.model.srv.TokenSrv;
import com.lifeplanner.model.srv.UserSrv;
import com.lifeplanner.model.vo.LoginVO;
import com.lifeplanner.model.vo.RegisterVO;
import com.lifeplanner.service.AuthService;
import com.lifeplanner.util.ApiPaths;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.Clock;
import java.time.Duration;

/**
 * Token endpoints. Responses that carry tokens are {@code Cache-Control: no-store} (RFC 6749 §5.1). The refresh token
 * travels only in an httpOnly, SameSite=Strict cookie scoped to {@code /api/v1/auth}; the access token only in the body
 * (the client keeps it in memory and sends {@code Authorization: Bearer …}).
 */
@Tag(name = "Authentication")
@RestController
@RequestMapping(ApiPaths.V1 + "/auth")
public class AuthController {

    static final String COOKIE_PATH = ApiPaths.V1 + "/auth";

    private final AuthService auth;
    private final PlannerProperties.Auth settings;
    private final Clock clock;

    public AuthController(AuthService auth, PlannerProperties properties, Clock clock) {
        this.auth = auth;
        this.settings = properties.auth();
        this.clock = clock;
    }

    /** Creates an account and signs it in (201). The first account ever created also receives all pre-existing data. */
    @SecurityRequirements
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an account and sign in", description = "Creates an account (e-mail is trimmed and lower-cased; password 8 characters to 72 UTF-8 bytes, stored as BCrypt) and starts a session: returns an access token in the body and sets the refresh-token cookie. The first account ever created on this database also receives all data that existed before authentication was added. Public: no token needed.")
    @ApiResponse(responseCode = "201", description = "Account created and signed in.", headers = {@Header(name = "Set-Cookie", description = "planner_refresh=<refresh token>; Path=/api/v1/auth; Max-Age=<seconds>; HttpOnly; SameSite=Strict — the refresh token (never in the body; not readable by scripts).", schema = @Schema(type = "string")), @Header(name = "Cache-Control", description = "no-store: responses with tokens must not be cached (RFC 6749 §5.1).", schema = @Schema(type = "string"))})
    @ApiResponse(responseCode = "409", description = "An account with this e-mail exists (code=email_taken).")
    @PostMapping("/register")
    public ResponseEntity<TokenSrv> register(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "E-mail, password and optional display name.", required = true) @Valid @RequestBody RegisterVO request) {
        return withSession(HttpStatus.CREATED, auth.register(request));
    }

    @SecurityRequirements
    @Operation(summary = "Sign in", description = "Checks e-mail (case-insensitive) and password and starts a new session (each login is an independent session, e.g. per device). Wrong e-mail and wrong password give the same 401 so the existence of an account is not revealed. After 5 failed attempts for an e-mail within 15 minutes further attempts get 429. Public: no token needed.")
    @ApiResponse(responseCode = "200", description = "Signed in.", headers = {@Header(name = "Set-Cookie", description = "planner_refresh=<refresh token>; Path=/api/v1/auth; Max-Age=<seconds>; HttpOnly; SameSite=Strict — the refresh token (never in the body; not readable by scripts).", schema = @Schema(type = "string")), @Header(name = "Cache-Control", description = "no-store: responses with tokens must not be cached (RFC 6749 §5.1).", schema = @Schema(type = "string"))})
    @ApiResponse(responseCode = "401", description = "Wrong e-mail or password (code=invalid_credentials).")
    @ApiResponse(responseCode = "429", description = "Too many failed attempts (code=too_many_attempts); see Retry-After.")
    @PostMapping("/login")
    public ResponseEntity<TokenSrv> login(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "E-mail and password.", required = true) @Valid @RequestBody LoginVO request) {
        return withSession(HttpStatus.OK, auth.login(request));
    }

    /** New access token + rotated refresh cookie. No body needed: the refresh token comes from the cookie. */
    @SecurityRequirements
    @Operation(summary = "Get a new access token", description = "Uses the refresh-token cookie (sent automatically by the browser; no body) to issue a new access token. The refresh token is rotated: the old one is revoked and a new cookie is set; the session keeps its original absolute expiry (30 days from login). Presenting an already rotated token again revokes the whole session (theft detection), except within 30 seconds of the rotation, which covers two browser tabs refreshing at once (answered with refresh_token_rotated: retry with the newer cookie). Public: no access token needed.")
    @ApiResponse(responseCode = "200", description = "New access token; rotated refresh cookie.", headers = {@Header(name = "Set-Cookie", description = "planner_refresh=<refresh token>; Path=/api/v1/auth; Max-Age=<seconds>; HttpOnly; SameSite=Strict — the refresh token (never in the body; not readable by scripts).", schema = @Schema(type = "string")), @Header(name = "Cache-Control", description = "no-store: responses with tokens must not be cached (RFC 6749 §5.1).", schema = @Schema(type = "string"))})
    @ApiResponse(responseCode = "401", description = "No, unknown, expired, revoked or reused refresh token (code=invalid_refresh_token; the cookie is cleared) or a parallel refresh just rotated it (code=refresh_token_rotated; cookie kept, retry).")
    @PostMapping("/refresh")
    public ResponseEntity<TokenSrv> refresh(@Parameter(description = "Refresh token set by register/login/refresh as an httpOnly cookie. Browsers send it automatically; it cannot be entered in Swagger UI (httpOnly).") @CookieValue(name = "${planner.auth.cookie-name:planner_refresh}", required = false) String refreshToken) {
        try {
            return withSession(HttpStatus.OK, auth.refresh(refreshToken));
        } catch (ApiException.UnauthorizedException e) {
            if (ErrorCodes.REFRESH_TOKEN_ROTATED.equals(e.code())) {
                throw e; // the browser already holds the newer cookie from the parallel refresh: keep it
            }
            // an unusable refresh cookie is removed so the browser stops sending it
            throw new CookieClearingException(e, clearCookie());
        }
    }

    /** Revokes the session and clears the cookie. 204, also when there was no (valid) session. */
    @SecurityRequirements
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Sign out", description = "Revokes the session of the refresh-token cookie (all its rotations; other devices stay signed in) and clears the cookie. Idempotent: also 204 without a (valid) cookie. Access tokens are stateless and stay valid until they expire. Public: no access token needed.")
    @ApiResponse(responseCode = "204", description = "Signed out; cookie cleared.", headers = {@Header(name = "Set-Cookie", description = "planner_refresh=; Max-Age=0 — deletes the refresh cookie.", schema = @Schema(type = "string"))})
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Parameter(description = "Refresh-token cookie of the session to end (sent automatically by the browser).") @CookieValue(name = "${planner.auth.cookie-name:planner_refresh}", required = false) String refreshToken) {
        auth.logout(refreshToken);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, clearCookie().toString())
                .cacheControl(CacheControl.noStore()).build();
    }

    @Operation(summary = "Current account", description = "The account the access token belongs to (the sub claim).")
    @ApiResponse(responseCode = "200", description = "The signed-in account.")
    @GetMapping("/me")
    public UserSrv me() {
        return auth.me();
    }

    private ResponseEntity<TokenSrv> withSession(HttpStatus status, AuthService.Session session) {
        Duration maxAge = Duration.between(clock.instant(), session.refreshExpiresAt());
        ResponseCookie cookie = baseCookie(session.refreshToken()).maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge).build();
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(session.tokens());
    }

    ResponseCookie clearCookie() {
        return baseCookie("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(settings.cookieName(), value)
                .httpOnly(true)
                .secure(settings.cookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH);
    }

    /** Carries the cookie-clearing header to {@link GlobalExceptionHandler} together with the original error. */
    public static class CookieClearingException extends RuntimeException {
        private final transient ResponseCookie clear;

        CookieClearingException(ApiException cause, ResponseCookie clear) {
            super(cause);
            this.clear = clear;
        }

        public ApiException original() {
            return (ApiException) getCause();
        }

        public ResponseCookie clear() {
            return clear;
        }
    }
}
