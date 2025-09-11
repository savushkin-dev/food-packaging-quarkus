package org.acme.foodpackaging.domain;

public class CleaningRule {
    private String parameter;
    private String from;
    private String to;
    private int duration;

    public CleaningRule(String parameter, String from, String to, int duration) {
        this.parameter = parameter;
        this.from = from;
        this.to = to;
        this.duration = duration;
    }

    public String getParameter() { return parameter; }
    public String getFrom() { return from; }
    public String getTo() { return to; }
    public int getDuration() { return duration; }
}

