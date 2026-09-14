package org.acme.foodpackaging.dto.row.products;

public record CleaningRuleRow(
        String parameter,
        String from,
        String to,
        int duration,
        boolean isPLRLC
) {}
