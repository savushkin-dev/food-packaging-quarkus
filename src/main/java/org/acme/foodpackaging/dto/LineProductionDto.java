package org.acme.foodpackaging.dto;

public record LineProductionDto(
        String lineNumber,
        String name,
        ShiftProductionDto shift1,
        ShiftProductionDto shift2,
        double totalMassa
) {
}
