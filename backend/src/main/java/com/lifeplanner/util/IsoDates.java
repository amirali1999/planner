package com.lifeplanner.util;

import com.lifeplanner.exception.ApiException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.regex.Pattern;

/** Range rules shared by date-only and timestamp inputs: storage uses 4-digit years (DB CHECKs). */
public final class IsoDates {

    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Instant MIN_INSTANT = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant END_INSTANT = Instant.parse("+10000-01-01T00:00:00Z");

    private IsoDates() {
    }

    public static boolean isStorable(LocalDate date) {
        return date == null || (date.getYear() >= 1 && date.getYear() <= 9999);
    }

    /**
     * Strict date-only parsing for path/query parameters: exactly {@code YYYY-MM-DD}, a real Gregorian date,
     * years 0001..9999. (Spring's default conversion would also accept forms such as "2026-11-03+03:30".)
     */
    public static LocalDate parseParam(String field, String value) {
        try {
            if (value != null && ISO_DATE.matcher(value).matches()) {
                LocalDate date = LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
                if (isStorable(date)) {
                    return date;
                }
            }
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        throw new ApiException.ValidationException("تاریخ باید به شکل YYYY-MM-DD و معتبر باشد.", Map.of(field, "invalid_value"));
    }

    /** Optional ISO-8601 UTC timestamp parameter (e.g. 2026-11-10T20:30:00Z); null/blank = absent. */
    public static Instant parseInstantParam(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            Instant instant = Instant.parse(value.trim());
            if (isStorable(instant)) {
                return instant;
            }
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        throw new ApiException.ValidationException("زمان باید به شکل ISO-8601 (UTC) و معتبر باشد.", Map.of(field, "invalid_value"));
    }

    public static boolean isStorable(Instant instant) {
        return instant == null || (!instant.isBefore(MIN_INSTANT) && instant.isBefore(END_INSTANT));
    }
}
