package com.lifeplanner.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Three statuses, like tasks (PRD FR-E01). Set by the user; never derived from linked work. */
public enum FocusStatus {
    NOT_STARTED("not_started"), DOING("doing"), DONE("done");

    private final String code;

    FocusStatus(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static FocusStatus fromCode(String code) {
        for (FocusStatus s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown focus item status: " + code);
    }
}
