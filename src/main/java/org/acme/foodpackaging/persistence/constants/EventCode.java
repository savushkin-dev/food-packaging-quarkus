package org.acme.foodpackaging.persistence.constants;

import lombok.Getter;

@Getter
public enum EventCode {
    START_FACT(1),
    START_CAMERA(2),
    END_CAMERA(3),
    PACKAGING_DELAY(10),
    CLEANING_DELAY(11),
    DRAW_CLEANING(12);

    private final int code;

    EventCode(int code) {
        this.code = code;
    }
}
