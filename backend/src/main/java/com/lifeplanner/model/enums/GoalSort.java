package com.lifeplanner.model.enums;

public enum GoalSort {
    /** period start, coarser period first, creation time, id (same as the tree's sibling order). */
    PERIOD("period"),
    /** dated goals first by deadline, undated last, then the period order. */
    DEADLINE("deadline"),
    /** the user's manual priority (1 first); archived goals (no priority) last, then the period order. */
    PRIORITY("priority");

    private final String code;

    GoalSort(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
