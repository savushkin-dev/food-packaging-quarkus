package org.acme.foodpackaging.service.downtime;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.acme.foodpackaging.dto.DowntimePeriodItem;
import org.acme.foodpackaging.dto.DowntimePeriodsResponse;
import org.acme.foodpackaging.repository.PmLogRepository;
import org.acme.foodpackaging.rest.ApiFields;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

@ApplicationScoped
public class DowntimePeriodsService {

    private static final Duration MIN_DOWNTIME = Duration.ofMinutes(2);

    private final PmLogRepository pmLogRepository;

    @Inject
    public DowntimePeriodsService(PmLogRepository pmLogRepository) {
        this.pmLogRepository = pmLogRepository;
    }

    public DowntimePeriodsResponse build(String idBatch) {
        try (Stream<LocalDateTime> dtsStream = pmLogRepository.streamMarkingDtsByIdBatch(idBatch)) {
            Iterator<LocalDateTime> iterator = dtsStream.iterator();
            if (!iterator.hasNext()) {
                throw new WebApplicationException(ApiFields.NO_PM_LOG_ROWS_FOR_BATCH, Response.Status.NOT_FOUND);
            }

            LocalDateTime cameraStart = iterator.next();
            LocalDateTime cameraEnd = cameraStart;
            LocalDateTime previous = cameraStart;

            List<DowntimePeriodItem> downtime = new ArrayList<>();
            while (iterator.hasNext()) {
                LocalDateTime current = iterator.next();
                cameraEnd = current;
                if (!current.isBefore(previous) && Duration.between(previous, current).compareTo(MIN_DOWNTIME) > 0) {
                    downtime.add(new DowntimePeriodItem(previous, current));
                }
                previous = current;
            }

            return new DowntimePeriodsResponse(idBatch, cameraStart, cameraEnd, List.copyOf(downtime));
        }
    }
}
