package com.lifeplanner.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Triage outcomes (docs/API_CONTRACT.md). The first three create an entity; the others only file the item. */
public enum InboxDecision {
    TASK("task", true), GOAL("goal", true), FOCUS_ITEM("focus_item", true),
    REFERENCE("reference", false), SOMEDAY("someday", false), DISCARD("discard", false);

    private final String code;
    private final boolean createsEntity;

    InboxDecision(String code, boolean createsEntity) {
        this.code = code;
        this.createsEntity = createsEntity;
    }

    @JsonValue
    public String code() {
        return code;
    }

    public boolean createsEntity() {
        return createsEntity;
    }

    @JsonCreator
    public static InboxDecision fromCode(String code) {
        for (InboxDecision d : values()) {
            if (d.code.equals(code)) {
                return d;
            }
        }
        throw new IllegalArgumentException("Unknown inbox decision: " + code);
    }
}
