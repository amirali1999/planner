package com.lifeplanner.model;

import java.time.Instant;

/** A reference to an existing Task on a plan; the Task row itself is never copied. */
public record DailyPlanItem(String id, String planId, String taskId, int position, Instant createdAt) {
}
