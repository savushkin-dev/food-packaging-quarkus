package org.acme.foodpackaging.scheduleoperations.utils;

import lombok.Getter;

@Getter
public enum EventCode {

    DRAW_CLEANING(12);

    private final int code;

    EventCode(int code) {
        this.code = code;
    }
}
