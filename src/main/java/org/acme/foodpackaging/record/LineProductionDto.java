package org.acme.foodpackaging.record;

import java.util.Map;

public record LineProductionDto(
        String lineNumber,
        double massa,
        String name,
        Map<String, Double> snpz
) {
    public LineProductionDto {
        snpz = snpz == null ? Map.of() : Map.copyOf(snpz);
    }
}
