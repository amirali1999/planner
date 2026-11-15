package com.lifeplanner.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Append-only KR measurement. */
public record CheckIn(String id, String keyResultId, BigDecimal value, Instant measuredAt, String note, Instant createdAt) {
}
