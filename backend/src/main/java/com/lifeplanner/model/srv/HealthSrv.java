package com.lifeplanner.model.srv;

import io.swagger.v3.oas.annotations.media.Schema;


@Schema(description = "Backend health; contains no personal data.")
public record HealthSrv(@Schema(description = "Always UP when the backend answers.", example = "UP") String status, @Schema(description = "API version.", example = "v1") String apiVersion) {
}
