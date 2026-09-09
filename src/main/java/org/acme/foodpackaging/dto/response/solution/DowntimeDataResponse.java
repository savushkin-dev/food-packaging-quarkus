package org.acme.foodpackaging.dto.response.solution;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.acme.foodpackaging.service.solution.value.DowntimeDataValue;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO endpoint-а
 */

public record DowntimeDataResponse(
        @JsonProperty("idbatch") String idBatch,
        LocalDateTime cameraStart,
        LocalDateTime cameraEnd,
        List<DowntimeDataValue.DowntimePeriodValue> downtime
) {}
