package com.lifeplanner.exception;

import org.springframework.http.HttpStatus;
import java.util.Map;

/**
 * Base for domain/service errors that map to a contract error response.
 * Feature code throws the concrete subclasses below instead of building responses itself.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> details;

    public ApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }

    /** 400 — shape, date or semantic validation failure. */
    public static class ValidationException extends ApiException {
        public ValidationException(String message, Map<String, Object> details) {
            super(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR, message, details);
        }

        public ValidationException(String message) {
            this(message, null);
        }
    }

    /** 404 — referenced entity does not exist. */
    public static class NotFoundException extends ApiException {
        public NotFoundException(String message, Map<String, Object> details) {
            super(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, message, details);
        }

        public NotFoundException(String message) {
            this(message, null);
        }
    }

    /**
     * 401 — missing/invalid credentials or token. {@code challenge} is the RFC 6750 {@code WWW-Authenticate} value
     * sent with the response.
     */
    public static class UnauthorizedException extends ApiException {
        private final String challenge;

        public UnauthorizedException(String code, String message, String challenge) {
            super(HttpStatus.UNAUTHORIZED, code, message, null);
            this.challenge = challenge;
        }

        public String challenge() {
            return challenge;
        }
    }

    /** 429 — too many attempts; {@code retryAfterSeconds} goes into {@code Retry-After}. */
    public static class TooManyRequestsException extends ApiException {
        private final long retryAfterSeconds;

        public TooManyRequestsException(String code, String message, long retryAfterSeconds) {
            super(HttpStatus.TOO_MANY_REQUESTS, code, message, Map.of("retryAfterSeconds", retryAfterSeconds));
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long retryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    /** 409 — duplicate, cycle or invalid transition. {@code code} may be more specific, e.g. duplicate_plan_task. */
    public static class ConflictException extends ApiException {
        public ConflictException(String code, String message, Map<String, Object> details) {
            super(HttpStatus.CONFLICT, code, message, details);
        }

        public ConflictException(String message) {
            this(ErrorCodes.CONFLICT, message, null);
        }
    }
}
