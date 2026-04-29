package org.acme.foodpackaging.sql;

import lombok.Getter;

@Getter
public enum EventTypeFilter {MAINTENANCE("""
        AND (v.SNPZ IN (0, 10) OR v.SNPZ IS NULL)
    """),

    DELAY("""
        AND v.EVTYPE = 10
    """),

    CLEANING_DELAY("""
        AND v.EVTYPE = 11
    """);

    private final String condition;

    EventTypeFilter(String condition) {
        this.condition = condition;
    }
}
