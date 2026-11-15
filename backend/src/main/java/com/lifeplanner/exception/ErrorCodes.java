package com.lifeplanner.exception;

/** Stable machine-readable error codes (docs/DOMAIN_RULES.md "Contract error codes"). */
public final class ErrorCodes {

    public static final String VALIDATION_ERROR = "validation_error";
    public static final String NOT_FOUND = "not_found";
    public static final String CONFLICT = "conflict";
    public static final String METHOD_NOT_ALLOWED = "method_not_allowed";
    public static final String UNSUPPORTED_MEDIA_TYPE = "unsupported_media_type";
    public static final String INTERNAL_ERROR = "internal_error";
    /** 401: no or unusable access token (see WWW-Authenticate). */
    public static final String UNAUTHORIZED = "unauthorized";
    public static final String INVALID_TOKEN = "invalid_token";
    public static final String INVALID_CREDENTIALS = "invalid_credentials";
    public static final String INVALID_REFRESH_TOKEN = "invalid_refresh_token";
    /** 401: the refresh token was just rotated by a parallel request (another tab); retry with the new cookie. */
    public static final String REFRESH_TOKEN_ROTATED = "refresh_token_rotated";
    public static final String EMAIL_TAKEN = "email_taken";
    public static final String TOO_MANY_ATTEMPTS = "too_many_attempts";

    private ErrorCodes() {
    }
}
