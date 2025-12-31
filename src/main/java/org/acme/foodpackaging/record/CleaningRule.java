package org.acme.foodpackaging.record;

import lombok.Getter;
import lombok.Setter;
import java.util.Objects;

@Getter
@Setter
public record CleaningRule(String parameter, String from, String to, int duration) {
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
}

