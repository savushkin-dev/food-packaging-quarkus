package org.acme.foodpackaging.service.load;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import org.acme.foodpackaging.dto.response.solution.DowntimeDataResponse;

import org.acme.foodpackaging.repository.PmLogRepository;
import org.acme.foodpackaging.service.solution.value.DowntimeDataValue;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DowntimeDataService {

    private static final Duration MIN_DOWNTIME = Duration.ofMinutes(2);

    private final PmLogRepository pmLogRepository;

    public DowntimeDataResponse build(String idBatch) {
        return build(idBatch, MIN_DOWNTIME);
    }

    public DowntimeDataResponse build(String idBatch, Duration minDowntime) {
        ZoneId zoneId = ZoneId.systemDefault();
        try (Stream<LocalDateTime> dtsStream = pmLogRepository.streamMarkingDtsByIdBatch(idBatch)) {
            Iterator<LocalDateTime> iterator = dtsStream.iterator();
            if (!iterator.hasNext()) {
                return new DowntimeDataResponse(idBatch, null, null, List.of());
            }

            LocalDateTime cameraStart = iterator.next();
            LocalDateTime cameraEnd = cameraStart;
            LocalDateTime previous = cameraStart;

            List<DowntimeDataValue.DowntimePeriodValue> downtime = new ArrayList<>();
            while (iterator.hasNext()) {
                LocalDateTime current = iterator.next();
                cameraEnd = current;
                if (!current.isBefore(previous) && Duration.between(previous.atZone(zoneId), current.atZone(zoneId)).compareTo(minDowntime) > 0) {
                    downtime.add(new DowntimeDataValue.DowntimePeriodValue(previous, current));
                }
                previous = current;
            }

            return new DowntimeDataResponse(idBatch, cameraStart, cameraEnd, List.copyOf(downtime));
        }
    }
}
