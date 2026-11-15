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
import com.lifeplanner.model.srv.HealthSrv;
import com.lifeplanner.util.ApiPaths;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Liveness probe. Must never include personal data, paths, versions of dependencies or secrets. */
@Tag(name = "System")
@RestController
@RequestMapping(ApiPaths.V1)
public class HealthController {

    private static final HealthSrv UP = new HealthSrv("UP", "v1");

    @SecurityRequirements
    @Operation(summary = "Health check", description = "Returns UP when the backend is running. Public, contains no personal data.")
    @ApiResponse(responseCode = "200", description = "Backend is up.")
    @GetMapping("/health")
    public HealthSrv health() {
        return UP;
    }
}
