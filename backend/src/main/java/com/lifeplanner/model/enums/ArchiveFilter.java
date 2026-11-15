package com.lifeplanner.model.enums;

import com.lifeplanner.exception.ApiException;
import java.util.Map;

/** Which archive state a list returns. Archived rows are only ever filtered, never dropped. */
public enum ArchiveFilter {
    ACTIVE, ARCHIVED, ALL;

    /** Parses the {@code archived} query parameter: false (default) | true | all. */
    public static ArchiveFilter fromParam(String value) {
        return switch (value == null ? "false" : value) {
            case "false" -> ACTIVE;
            case "true" -> ARCHIVED;
            case "all" -> ALL;
            default -> throw new ApiException.ValidationException("پارامتر جست‌وجو معتبر نیست.", Map.of("archived", "invalid_value"));
        };
    }

    /** SQL condition on an {@code archived_at} column (no user input involved). */
    public String sqlCondition(String column) {
        return switch (this) {
            case ACTIVE -> column + " IS NULL";
            case ARCHIVED -> column + " IS NOT NULL";
            case ALL -> "1 = 1";
        };
    }
}
