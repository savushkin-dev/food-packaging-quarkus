package org.acme.foodpackaging.service.lines.value;

import java.util.List;

public record LineProductionValue(
                String name,
                double massa,
                double massa1,
                double massa2,
                List<BatchProductionValue> shift1,
                List<BatchProductionValue> shift2) {
        public LineProductionValue {
                shift1 = shift1 == null ? List.of() : List.copyOf(shift1);
                shift2 = shift2 == null ? List.of() : List.copyOf(shift2);
        }
}