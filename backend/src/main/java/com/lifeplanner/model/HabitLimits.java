package com.lifeplanner.model;

/** Limits shared by the request validation (VOs) and the service checks. */
public final class HabitLimits {

    public static final int TITLE_MAX = 200;
    /** Longest range GET /habits accepts (from..to inclusive). */
    public static final int MAX_RANGE_DAYS = 62;

    private HabitLimits() {
    }
}
