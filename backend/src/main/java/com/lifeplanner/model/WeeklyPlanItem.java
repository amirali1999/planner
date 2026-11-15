package com.lifeplanner.model;

import java.time.Instant;
import java.time.LocalDate;

/** A task put on a Saturday–Friday week without a day. Owned by the user; only membership, no commitment data. */
public record WeeklyPlanItem(String id, LocalDate weekStart, String taskId, Instant createdAt) {
}
