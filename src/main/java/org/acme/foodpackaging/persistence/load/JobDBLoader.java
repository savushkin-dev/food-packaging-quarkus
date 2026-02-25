package org.acme.foodpackaging.persistence.load;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.dto.DbMaintenanceRow;
import org.acme.foodpackaging.record.FactKey;
import org.acme.foodpackaging.record.FactProductionRow;
import org.acme.foodpackaging.record.CameraFactRow;
import org.acme.foodpackaging.record.CameraValue;
import org.acme.foodpackaging.sql.SqlQueries;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.acme.foodpackaging.sql.SqlQueries.*;

@ApplicationScoped
public class JobDBLoader {

    @Inject
    EntityManager em;
    @Inject
    SqlQueries queries;

    @SuppressWarnings("unchecked")
    public Map<Long, DbJobRow> loadJobRowMap(
            LocalDateTime from,
            LocalDateTime to,
            String ksk
    ) {

        List<DbJobRow> rows = em
                .createNativeQuery(queries.loadJobs(), "DbJobRowMapping")
                .setParameter(1, Timestamp.valueOf(from))
                .setParameter(2, Timestamp.valueOf(to))
                .setParameter(3, ksk)
                .setParameter(4, Timestamp.valueOf(from))
                .getResultList();

        return rows.stream()
                .collect(Collectors.toMap(
                        DbJobRow::snpz,
                        r -> r,
                        (existing, duplicate) -> {
                            throw new IllegalStateException(
                                    "Duplicate SNPZ: " + existing.snpz()
                            );
                        }
                ));
    }

    @SuppressWarnings("unchecked")
    public List<DbMaintenanceRow> loadMaintenanceRows(
            LocalDateTime from,
            LocalDateTime to
    ) {

        return   em
                .createNativeQuery(queries.loadMaintenance(), "DbMaintenanceRowMapping")
                .setParameter(1, Timestamp.valueOf(from))
                .setParameter(2, Timestamp.valueOf(to))
                .setParameter(3, Timestamp.valueOf(from))
                .setParameter(4, Timestamp.valueOf(to))
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    public Map<FactKey, FactProductionRow> loadFactProductionRowMap(LocalDateTime from, LocalDateTime to) {

        List<FactProductionRow> rows = em
                .createNativeQuery(queries.loadFact(), "FactProductionRowMapping")
                .setParameter(1, Timestamp.valueOf(from))
                .setParameter(2, Timestamp.valueOf(to))
                .getResultList();

        return rows.stream()
                .collect(Collectors.toMap(
                        r -> new FactKey(r.kmc(), r.np(), r.eventType()),
                        Function.identity(),
                        (existing, duplicate) -> existing // Keep first occurrence, skip duplicates
                ));
    }

    @SuppressWarnings("unchecked")
    public Map<String, CameraValue> loadCameraRowMap(List<Job> jobs) {
    
        Map<String, CameraValue> result = new HashMap<>();
        Set<String> processedBatches = new HashSet<>();
    
        for (Job job : jobs) {
            String idBatch = job.getIdBatch();
            if (idBatch != null && !processedBatches.contains(idBatch)) {
                processedBatches.add(idBatch);
                List<CameraFactRow> rows = em
                        .createNativeQuery(queries.loadCameraFact(), "CameraFactRowMapping")
                        .setParameter(1, idBatch)
                        .getResultList();
                if (!rows.isEmpty()) {
                    CameraFactRow row = rows.getFirst();
                    result.put(
                            idBatch,
                            new CameraValue(
                                    row.cameraStart() != null ? row.cameraStart().toLocalDateTime() : null,
                                    row.cameraEnd() != null ? row.cameraEnd().toLocalDateTime() : null
                            )
                    );
                }
            }
        }
        return result;
    }
}

