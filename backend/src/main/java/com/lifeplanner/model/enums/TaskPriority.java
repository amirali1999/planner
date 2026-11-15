package com.lifeplanner.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Optional task priority. The docs do not fix a vocabulary; this API-level choice is validated here
 * (the DB column is unconstrained TEXT, so a later vocabulary change needs no table rebuild).
 */
public enum TaskPriority {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high");

    private final String code;

    TaskPriority(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static TaskPriority fromCode(String code) {
        for (TaskPriority p : values()) {
            if (p.code.equals(code)) {
                return p;
            }
        }
        throw new IllegalArgumentException("Unknown task priority: " + code);
    }
}
