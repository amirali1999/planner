package com.lifeplanner.model;

import com.lifeplanner.model.enums.ActivityCategory;
import java.time.Instant;
import java.time.LocalDate;

/** One actual activity performed during [startMinute, endMinute) on a local calendar day. */
public record ActivityLog(
        String id,
        LocalDate date,
        String title,
        ActivityCategory category,
        int startMinute,
        int endMinute,
        String taskId,
        Instant createdAt,
        Instant updatedAt) {
}
