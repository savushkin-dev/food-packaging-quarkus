package org.acme.foodpackaging.service.lines.value;

import java.time.LocalDateTime;

public record BatchProductionValue(
        String snpz,
        double massa,
        Integer np,
        LocalDateTime dts,
        LocalDateTime dte) {
}
