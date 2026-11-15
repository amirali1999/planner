package com.lifeplanner.model;

/** Shared validation limits for actual daily activity logs. */
public final class ActivityLogLimits {
    public static final int TITLE_MAX = 200;
    public static final int DAY_MINUTES = 1440;
    public static final int STEP_MINUTES = 5;

    private ActivityLogLimits() {
    }
}
