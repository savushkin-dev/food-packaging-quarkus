package org.acme.foodpackaging.record;

public record CleaningRule(
        String parameter,
        String from,
        String to,
        int duration,
        boolean isPLRLC
) {}
