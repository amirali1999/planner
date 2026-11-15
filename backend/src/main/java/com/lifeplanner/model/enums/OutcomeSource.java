package com.lifeplanner.model.enums;

public enum OutcomeSource {
    OWN_KEY_RESULTS("own_key_results"), CHILD_ROLLUP("child_rollup"), NOT_MEASURED("not_measured");

    private final String code;

    OutcomeSource(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
