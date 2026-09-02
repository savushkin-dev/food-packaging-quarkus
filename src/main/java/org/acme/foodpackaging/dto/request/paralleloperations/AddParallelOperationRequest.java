package org.acme.foodpackaging.dto.request.paralleloperations;

import java.time.Duration;
import java.time.LocalDateTime;

public record AddParallelOperationRequest(
        String lineId,
        LocalDateTime startDateTime,
        Duration duration,
        Integer eventTypeId,
        String note) {
}
