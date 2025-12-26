package org.acme.foodpackaging.repository.lines;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.Line;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.entity.lines.LineEntity;
import org.acme.foodpackaging.factory.LineFactory;
import org.acme.foodpackaging.persistence.load.LoadDataService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static org.acme.foodpackaging.scheduleOperations.utils.ScheduleUtils.fixLineJobs;
import static org.acme.foodpackaging.scheduleOperations.utils.ScheduleUtils.pinnAllLines;

@ApplicationScoped
public class LineRepository  implements PanacheRepository<LineEntity> {
    /**
     * Загружает id и название линии
     */
    public ConcurrentMap<String, String> loadLines() {
        List<LineEntity> lines = list("fDel = 0");

        return lines.stream()
                .collect(Collectors.toConcurrentMap(
                        e -> e.getKrc().trim(),
                        e -> e.getSnm().trim(),
                        (existing, replacement) -> existing
                ));
    }
}
