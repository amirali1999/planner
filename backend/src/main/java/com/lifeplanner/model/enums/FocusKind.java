package com.lifeplanner.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** What a Focus Item is. Validated in the API; the V1 column only requires a non-empty value. */
public enum FocusKind {
    PROJECT("project"), BOOK("book"), COURSE("course"), AREA("area"), OTHER("other");

    private final String code;

    FocusKind(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static FocusKind fromCode(String code) {
        for (FocusKind k : values()) {
            if (k.code.equals(code)) {
                return k;
            }
        }
        throw new IllegalArgumentException("Unknown focus item kind: " + code);
    }
}
