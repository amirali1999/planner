package com.lifeplanner.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Exactly three statuses; "blocked" is a separate blockedReason, not a fourth status. */
public enum TaskStatus {
    NOT_STARTED("not_started"),
    DOING("doing"),
    DONE("done");

    private final String code;

    TaskStatus(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static TaskStatus fromCode(String code) {
        for (TaskStatus s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown task status: " + code);
    }
}
