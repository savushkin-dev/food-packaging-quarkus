package org.acme.foodpackaging.service.downtime;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.acme.foodpackaging.dto.DowntimePeriodItem;
import org.acme.foodpackaging.dto.DowntimePeriodsResponse;
import org.acme.foodpackaging.record.PmLogMarkingRow;
import org.acme.foodpackaging.repository.PmLogRepository;
import org.acme.foodpackaging.rest.ApiFields;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class DowntimePeriodsService {

    private static final Duration MIN_DOWNTIME = Duration.ofMinutes(2);

    private final PmLogRepository pmLogRepository;

    @Inject
    public DowntimePeriodsService(PmLogRepository pmLogRepository) {
        this.pmLogRepository = pmLogRepository;
    }

    public DowntimePeriodsResponse build(String idBatch) {
        List<PmLogMarkingRow> rows = pmLogRepository.findMarkingRowsByIdBatch(idBatch);
        if (rows.isEmpty()) {
            throw new WebApplicationException(ApiFields.NO_PM_LOG_ROWS_FOR_BATCH, Response.Status.NOT_FOUND);
        }

        LocalDateTime cameraStart = rows.getFirst().dts();
        LocalDateTime cameraEnd = rows.getLast().dts();

        List<DowntimePeriodItem> downtime = new ArrayList<>();
        for (int i = 0; i < rows.size() - 1; i++) {
            LocalDateTime a = rows.get(i).dts();
            LocalDateTime b = rows.get(i + 1).dts();
            if (a == null || b == null || b.isBefore(a)) {
                continue;
            }
            if (Duration.between(a, b).compareTo(MIN_DOWNTIME) > 0) {
                downtime.add(new DowntimePeriodItem(a, b));
            }
        }

        return new DowntimePeriodsResponse(idBatch, cameraStart, cameraEnd, List.copyOf(downtime));
    }
}
