package com.lifeplanner.model;

/** Length/size limits shared by the request validation (VOs) and the service checks. */
public final class FocusItemLimits {

    public static final int TITLE_MAX = 200;
    public static final int DESCRIPTION_MAX = 2000;

    private FocusItemLimits() {
    }
}
