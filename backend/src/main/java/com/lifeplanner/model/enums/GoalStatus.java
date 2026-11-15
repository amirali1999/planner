package com.lifeplanner.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Set explicitly by the user; never derived from Outcome or Execution progress. */
public enum GoalStatus {
    NOT_STARTED("not_started"),
    DOING("doing"),
    DONE("done"),
    ON_HOLD("on_hold"),
    DROPPED("dropped");

    private final String code;

    GoalStatus(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static GoalStatus fromCode(String code) {
        for (GoalStatus s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown goal status: " + code);
    }
}
