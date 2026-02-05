package org.acme.foodpackaging.record;

import java.util.Objects;

public record CleaningRule(String parameter, String from, String to, int duration,  boolean isPLRLC) {
    @Override
    public String parameter() {
        return Objects.requireNonNullElse(parameter, "");
    }

    @Override
    public String from() {
        return from == null ? "" : from;
    }

    @Override
    public String to() {
        return to == null ? "" : to;
    }

    @Override
    public boolean isPLRLC() {
        return Objects.requireNonNullElse(isPLRLC, false);
    }
}