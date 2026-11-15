package com.lifeplanner.model;

/** Limits shared by the request validation (VOs) and the service checks. */
public final class TimeBlockLimits {

    public static final int TITLE_MAX = 200;
    /** Minutes are multiples of this step. */
    public static final int STEP_MINUTES = 5;
    public static final int DAY_MINUTES = 1440;
    /** Longest range GET /time-blocks accepts (from..to inclusive). */
    public static final int MAX_RANGE_DAYS = 42;

    private TimeBlockLimits() {
    }
}
