package com.lifeplanner.model;

import com.lifeplanner.model.enums.TimeBlockColor;
import java.time.Instant;
import java.time.LocalDate;

/** Planned time on one local day: [startMinute, endMinute) minutes after local midnight. Owned by the user. */
public record TimeBlock(
        String id,
        LocalDate date,
        int startMinute,
        int endMinute,
        String title,
        String taskId,
        TimeBlockColor color,
        Instant createdAt,
        Instant updatedAt) {
}
