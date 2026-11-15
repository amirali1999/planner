package com.lifeplanner.model.enums;

public enum InboxStatus {
    UNPROCESSED("unprocessed"), PROCESSED("processed");

    private final String code;

    InboxStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static InboxStatus fromCode(String code) {
        for (InboxStatus s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown inbox status: " + code);
    }
}
