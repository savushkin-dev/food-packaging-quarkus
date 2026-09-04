package org.acme.foodpackaging.dto;

import java.util.Map;

public record ShiftProductionDto(
        double massa,
        Map<String, Double> snpz) {
    public ShiftProductionDto {
        snpz = snpz == null ? Map.of() : Map.copyOf(snpz);
    }
}
