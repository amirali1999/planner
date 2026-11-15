package com.lifeplanner.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Colour of a time block (palette key; the UI maps it to theme colours). Mirrors the V12 CHECK. */
public enum TimeBlockColor {
    TEAL("teal"),
    BLUE("blue"),
    PURPLE("purple"),
    GREEN("green"),
    OLIVE("olive"),
    AMBER("amber"),
    ROSE("rose"),
    SLATE("slate");

    private final String code;

    TimeBlockColor(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static TimeBlockColor fromCode(String code) {
        for (TimeBlockColor c : values()) {
            if (c.code.equals(code)) {
                return c;
            }
        }
        throw new IllegalArgumentException("Unknown time block colour: " + code);
    }
}
