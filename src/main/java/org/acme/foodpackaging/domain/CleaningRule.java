package org.acme.foodpackaging.domain;

import lombok.Getter;

public class CleaningRule {
    private final String parameter;
    private final String from;
    private final String to;
    @Getter
    private int duration;

    public CleaningRule(String parameter, String from, String to, int duration) {
        this.parameter = parameter;
        this.from = from;
        this.to = to;
        this.duration = duration;
    }

    public String getParameter() { return parameter == null ? "" : parameter; }
    public String getFrom() { return from == null ? "" : from; }
    public String getTo() { return to == null ? "" : to; }
}

