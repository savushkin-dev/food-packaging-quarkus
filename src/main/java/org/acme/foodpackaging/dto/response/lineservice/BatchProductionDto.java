package org.acme.foodpackaging.dto.response.lineservice;

import java.time.LocalDateTime;

public record BatchProductionDto(
        String snpz,
        double massa,
        Integer np,
        LocalDateTime dts,
        LocalDateTime dte) {
}
