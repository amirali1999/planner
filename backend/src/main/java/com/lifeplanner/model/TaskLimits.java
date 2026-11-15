package com.lifeplanner.model;

/** Length/size limits shared by the request validation (VOs) and the service checks. */
public final class TaskLimits {

    public static final int TITLE_MAX = 200;
    public static final int DESCRIPTION_MAX = 2000;
    public static final int BLOCKED_REASON_MAX = 1000;
    public static final int ESTIMATE_MAX = 525_600;

    private TaskLimits() {
    }
}
