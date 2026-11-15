package com.lifeplanner.controller;

import com.lifeplanner.exception.ApiException;
import com.lifeplanner.exception.ErrorCodes;
import com.lifeplanner.model.srv.ApiErrorSrv;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps every exception to {@link ApiErrorSrv}. Never exposes stack traces, exception class names
 * or internal messages for unexpected errors.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    static final String MSG_VALIDATION = "داده‌های ارسال‌شده معتبر نیست.";
    static final String MSG_MALFORMED = "بدنه درخواست قابل خواندن نیست.";
    static final String MSG_NOT_FOUND = "منبع درخواستی پیدا نشد.";
    static final String MSG_METHOD = "این متد برای این مسیر پشتیبانی نمی‌شود.";
    static final String MSG_MEDIA_TYPE = "نوع محتوای درخواست پشتیبانی نمی‌شود.";
    static final String MSG_INTERNAL = "خطای داخلی رخ داد. لطفاً دوباره تلاش کنید.";

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiErrorSrv> handleApi(ApiException ex) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(ex.status());
        if (ex instanceof ApiException.UnauthorizedException u && u.challenge() != null) {
            response.header(org.springframework.http.HttpHeaders.WWW_AUTHENTICATE, u.challenge());
        }
        if (ex instanceof ApiException.TooManyRequestsException t) {
            response.header(org.springframework.http.HttpHeaders.RETRY_AFTER, Long.toString(t.retryAfterSeconds()));
        }
        return response.body(new ApiErrorSrv(ex.code(), ex.getMessage(), ex.details()));
    }

    /** A failed refresh: the original error plus a header that deletes the unusable refresh cookie. */
    @ExceptionHandler(AuthController.CookieClearingException.class)
    ResponseEntity<ApiErrorSrv> handleCookieClearing(AuthController.CookieClearingException ex) {
        ResponseEntity<ApiErrorSrv> base = handleApi(ex.original());
        return ResponseEntity.status(base.getStatusCode())
                .headers(h -> {
                    h.addAll(base.getHeaders());
                    h.add(org.springframework.http.HttpHeaders.SET_COOKIE, ex.clear().toString());
                })
                .body(base.getBody());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorSrv> handleBodyValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return validation(fields);
    }

    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
    ResponseEntity<ApiErrorSrv> handleParamValidation(Exception ex) {
        return validation(null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrorSrv> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return validation(Map.of(ex.getName(), "invalid_value"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiErrorSrv> handleMissingParam(MissingServletRequestParameterException ex) {
        return validation(Map.of(ex.getParameterName(), "required"));
    }

    /**
     * Body could not be bound. When Jackson knows which field failed (bad enum, impossible date, string
     * instead of number, unknown field) report it in details; raw parser messages are never exposed.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorSrv> handleUnreadable(HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
            String field = jsonPath(mapping);
            String reason = mapping instanceof UnrecognizedPropertyException ? "unknown_field" : "invalid_value";
            return validation(Map.of(field, reason));
        }
        return ResponseEntity.badRequest().body(ApiErrorSrv.of(ErrorCodes.VALIDATION_ERROR, MSG_MALFORMED));
    }

    private static String jsonPath(JsonMappingException ex) {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference ref : ex.getPath()) {
            if (ref.getFieldName() != null) {
                path.append(path.isEmpty() ? "" : ".").append(ref.getFieldName());
            } else {
                path.append('[').append(ref.getIndex()).append(']');
            }
        }
        return path.toString();
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ResponseEntity<ApiErrorSrv> handleNoRoute(Exception ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiErrorSrv.of(ErrorCodes.NOT_FOUND, MSG_NOT_FOUND));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiErrorSrv> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiErrorSrv.of(ErrorCodes.METHOD_NOT_ALLOWED, MSG_METHOD));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiErrorSrv> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiErrorSrv.of(ErrorCodes.UNSUPPORTED_MEDIA_TYPE, MSG_MEDIA_TYPE));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorSrv> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorSrv.of(ErrorCodes.INTERNAL_ERROR, MSG_INTERNAL));
    }

    private static ResponseEntity<ApiErrorSrv> validation(Map<String, Object> details) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorSrv(ErrorCodes.VALIDATION_ERROR, MSG_VALIDATION, details == null || details.isEmpty() ? null : details));
    }
}
