package com.lifeplanner.controller;

import com.lifeplanner.exception.ErrorCodes;
import com.lifeplanner.model.srv.ApiErrorSrv;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Spring Boot's default /error body (timestamp/path/trace) so that errors raised
 * outside the MVC handler chain (e.g. container-level failures) still follow {@link ApiErrorSrv}.
 */
@RestController
public class ApiErrorController implements ErrorController {

    @RequestMapping("${server.error.path:/error}")
    ResponseEntity<ApiErrorSrv> error(HttpServletRequest request) {
        HttpStatus status = resolveStatus(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE));
        ApiErrorSrv body = switch (status) {
            case NOT_FOUND -> ApiErrorSrv.of(ErrorCodes.NOT_FOUND, GlobalExceptionHandler.MSG_NOT_FOUND);
            case METHOD_NOT_ALLOWED -> ApiErrorSrv.of(ErrorCodes.METHOD_NOT_ALLOWED, GlobalExceptionHandler.MSG_METHOD);
            case INTERNAL_SERVER_ERROR -> ApiErrorSrv.of(ErrorCodes.INTERNAL_ERROR, GlobalExceptionHandler.MSG_INTERNAL);
            default -> ApiErrorSrv.of(ErrorCodes.VALIDATION_ERROR, GlobalExceptionHandler.MSG_VALIDATION);
        };
        return ResponseEntity.status(status).body(body);
    }

    /** Any non-4xx (or unknown) status is reported as a sanitized 500. */
    private static HttpStatus resolveStatus(Object code) {
        HttpStatus resolved = code instanceof Integer value ? HttpStatus.resolve(value) : null;
        return resolved != null && resolved.is4xxClientError() ? resolved : HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
