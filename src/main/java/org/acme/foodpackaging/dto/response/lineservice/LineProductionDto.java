package org.acme.foodpackaging.dto.response.lineservice;

import java.util.List;

public record LineProductionDto(
                String name,
                double massa,
                double massa1,
                double massa2,
                List<BatchProductionDto> shift1,
                List<BatchProductionDto> shift2) {
        public LineProductionDto {
                shift1 = shift1 == null ? List.of() : List.copyOf(shift1);
                shift2 = shift2 == null ? List.of() : List.copyOf(shift2);
        }
}