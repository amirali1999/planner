package com.lifeplanner.model.srv;

import io.swagger.v3.oas.annotations.media.Schema;
import com.lifeplanner.model.CheckIn;
import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "One recorded measurement of a Key Result (append-only).")
public record CheckInSrv(@Schema(description = "Check-in id.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String id, @Schema(description = "Key Result measured.", example = "0b6d1c3e-5a7f-4c2e-9d1b-2f8e6a4c7b10") String keyResultId, @Schema(description = "Measured value (exact decimal).", example = "40") BigDecimal value, @Schema(description = "When the value was measured (UTC).", example = "2026-11-09T08:30:00.000Z") Instant measuredAt, @Schema(description = "Optional note; null when none.", example = "evaluation set v1") String note,
                              @Schema(description = "When the check-in was recorded (UTC).", example = "2026-11-09T08:30:00.000Z") Instant createdAt) {
    public static CheckInSrv from(CheckIn c) {
        return new CheckInSrv(c.id(), c.keyResultId(), c.value(), c.measuredAt(), c.note(), c.createdAt());
    }
}
