package com.lifeplanner.db;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Timestamp persistence: fixed-width UTC text {@code yyyy-MM-dd'T'HH:mm:ss.SSS'Z'}.
 * {@link Instant#toString()} is NOT used because its variable fraction length breaks lexical ordering
 * ("...:00Z" would sort after "...:00.5Z"), which ORDER BY measured_at relies on.
 */
public final class UtcTimestamps {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private UtcTimestamps() {
    }

    /** Truncates to milliseconds. Years outside 0000..9999 are rejected. */
    public static String toDb(Instant instant) {
        String text = FORMAT.format(instant.truncatedTo(ChronoUnit.MILLIS));
        if (text.length() != 24) {
            throw new IllegalArgumentException("Timestamp year out of range: " + instant);
        }
        return text;
    }

    public static Instant fromDb(String text) {
        return text == null ? null : Instant.parse(text);
    }
}
