package org.acme.foodpackaging.dto.request.lines;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record LineTimeUpdateRequest(
        String lineId,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm[:ss]")
        LocalDateTime startLineDateTime,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm[:ss]")
        LocalDateTime lineMaxEndDateTime
) {}
