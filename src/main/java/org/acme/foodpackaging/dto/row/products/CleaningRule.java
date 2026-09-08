package org.acme.foodpackaging.dto.row.products;

public record CleaningRule(
        String parameter,
        String from,
        String to,
        int duration,
        boolean isPLRLC
) {}
