package com.lifeplanner.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Goal period granularity; {@link #rank} orders coarse (annual) to fine (weekly). */
public enum PeriodType {
    ANNUAL("annual", 4),
    QUARTERLY("quarterly", 3),
    MONTHLY("monthly", 2),
    WEEKLY("weekly", 1);

    private final String code;
    private final int rank;

    PeriodType(String code, int rank) {
        this.code = code;
        this.rank = rank;
    }

    @JsonValue
    public String code() {
        return code;
    }

    /** True if this period is strictly coarser than {@code child}, as required for a parent Goal. */
    public boolean isCoarserThan(PeriodType child) {
        return rank > child.rank;
    }

    @JsonCreator
    public static PeriodType fromCode(String code) {
        for (PeriodType t : values()) {
            if (t.code.equals(code)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown periodType: " + code);
    }
}
