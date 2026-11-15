package com.lifeplanner.model.srv;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
@Schema(description = "Persisted running activity timer. No completed interval exists until stop.")
public record ActivityTimerSrv(
 @Schema(description = "Activity title.", example = "مطالعه") String title,
 @Schema(description = "Actual start instant in UTC.", example = "2026-11-14T07:00:00Z") Instant startedAt,
 @Schema(description = "IANA timezone captured at start.", example = "Asia/Tehran") String timeZone) {}
