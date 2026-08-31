package org.acme.foodpackaging.persistence.constants;

public enum DelayEventType {
    PACKAGING(10),
    CLEANING(11);

    private final int code;
    DelayEventType(int code) { this.code = code; }
    public int code() { return code; }
}
