package org.acme.foodpackaging.service.solution.value;

import java.time.LocalDateTime;
import java.util.Map;

public record DowntimeDataValue(String planningDate, long downtime, Map<String, Long> lines) {
    /**
     * DTO-record для одного интервала простоя
     */

    public static record DowntimePeriodValue(LocalDateTime dtStart, LocalDateTime dtEnd) {}
}
