package com.lifeplanner.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** User-defined category of an activity that actually happened. Mirrors the V16 CHECK. */
public enum ActivityCategory {
    WORK("work"), STUDY("study"), REST("rest"), PERSONAL("personal");

    private final String code;

    ActivityCategory(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static ActivityCategory fromCode(String code) {
        for (ActivityCategory category : values()) {
            if (category.code.equals(code)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown activity category: " + code);
    }
}
