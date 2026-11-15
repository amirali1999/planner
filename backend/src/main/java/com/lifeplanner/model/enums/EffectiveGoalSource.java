package com.lifeplanner.model.enums;

public enum EffectiveGoalSource {
    DIRECT("direct"), FOCUS_ITEM("focus_item");

    private final String code;

    EffectiveGoalSource(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
