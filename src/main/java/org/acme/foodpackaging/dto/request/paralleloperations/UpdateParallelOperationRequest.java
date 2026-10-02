package org.acme.foodpackaging.dto.request.paralleloperations;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record UpdateParallelOperationRequest(
        String id,
        String lineId,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime startDateTime,
        Integer duration,
        Integer eventTypeId,
        String note) {
}
