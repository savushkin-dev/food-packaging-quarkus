package org.acme.foodpackaging.domain;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Builder;
import lombok.Getter;
import org.acme.foodpackaging.persistence.serializer.DurationMinutesSerializer;

import java.time.Duration;
import java.time.LocalDateTime;

@Getter
@Builder(toBuilder = true)
public class ParallelOperation {

    private final String id;
    private final String lineId;
    private final String name;
    private final LocalDateTime startDateTime;
    private final LocalDateTime endDateTime;
    @JsonSerialize(using = DurationMinutesSerializer.class)
    private final Duration duration;
    private final Integer eventTypeId;
    private final String note;
}
