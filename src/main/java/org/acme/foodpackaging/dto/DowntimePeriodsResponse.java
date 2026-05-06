package org.acme.foodpackaging.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO endpoint-а
 */

public record DowntimePeriodsResponse(
        @JsonProperty("idbatch") String idBatch,
        LocalDateTime cameraStart,
        LocalDateTime cameraEnd,
        List<DowntimePeriodItem> downtime
) {}
