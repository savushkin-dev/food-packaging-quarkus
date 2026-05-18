package org.acme.foodpackaging.persistence.load;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.dto.DbMaintenanceRow;
import org.acme.foodpackaging.record.FactKey;
import org.acme.foodpackaging.record.FactProductionRow;
import org.acme.foodpackaging.record.CameraFactRow;
import org.acme.foodpackaging.record.CameraValue;
import org.acme.foodpackaging.sql.EventTypeFilter;
import org.acme.foodpackaging.sql.SqlQueries;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class JobDBLoader {

    private static final String DB_JOB_ROW_MAPPING = "DbJobRowMapping";
    private static final String DB_MAINTENANCE_ROW_MAPPING = "DbMaintenanceRowMapping";
    private static final String FACT_PRODUCTION_MAPPING = "FactProductionRowMapping";
    private static final String CAMERA_FACT_MAPPING = "CameraFactRowMapping";

    private final EntityManager em;
    private final SqlQueries queries;

    @Inject
    public JobDBLoader(EntityManager em, SqlQueries queries) {
        this.em = em;
        this.queries = queries;
    }

    // ========================= JOBS =========================

    public Map<Long, DbJobRow> loadJobRowMap(
            LocalDateTime from,
            LocalDateTime to,
            String ksk
    ) {
        List<DbJobRow> rows = getResultList(
                queries.loadJobs(),
                DB_JOB_ROW_MAPPING,
                from, to, ksk, from
        );

        return rows.stream()
                .collect(Collectors.toMap(
                        DbJobRow::snpz,
                        Function.identity(),
                        (existing, duplicate) -> {
                            throw new IllegalStateException("Duplicate SNPZ: " + existing.snpz());
                        }
                ));
    }

    // ========================= MAINTENANCE / DELAYS =========================

    public List<DbMaintenanceRow> loadMaintenanceRows(LocalDateTime from, LocalDateTime to) {
        return loadMaintenanceByType(EventTypeFilter.MAINTENANCE, from, to);
    }

    public Map<Long, DbMaintenanceRow> loadCleaningRows(LocalDateTime from, LocalDateTime to) {
        return toMapBySnpz(loadMaintenanceByType(EventTypeFilter.CLEANING, from, to));
    }

    public Map<Long, DbMaintenanceRow> loadDelayDurationRows(LocalDateTime from, LocalDateTime to) {
        return toMapBySnpz(loadMaintenanceByType(EventTypeFilter.DELAY, from, to));
    }

    public Map<Long, DbMaintenanceRow> loadCleaningDelayDurationRows(LocalDateTime from, LocalDateTime to) {
        return toMapBySnpz(loadMaintenanceByType(EventTypeFilter.CLEANING_DELAY, from, to));
    }

    private List<DbMaintenanceRow> loadMaintenanceByType(
            EventTypeFilter type,
            LocalDateTime from,
            LocalDateTime to
    ) {
        return getResultList(
                queries.loadOeePev(type),
                DB_MAINTENANCE_ROW_MAPPING,
                from, to, from, to
        );
    }

    private Map<Long, DbMaintenanceRow> toMapBySnpz(List<DbMaintenanceRow> list) {
        return list.stream()
                .collect(Collectors.toMap(
                        DbMaintenanceRow::getSnpz,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
    }

    // ========================= FACT =========================

    public Map<FactKey, FactProductionRow> loadFactProductionRowMap(
            LocalDateTime from,
            LocalDateTime to
    ) {
        List<FactProductionRow> rows = getResultList(
                queries.loadFact(),
                FACT_PRODUCTION_MAPPING,
                from, to
        );

        return rows.stream()
                .collect(Collectors.toMap(
                        r -> new FactKey(r.kmc(), r.np(), r.eventType()),
                        Function.identity(),
                        (existing, duplicate) -> existing
                ));
    }

    // ========================= CAMERA =========================

    public Map<String, CameraValue> loadCameraRowMap(List<Job> jobs) {

        Map<String, CameraValue> result = new HashMap<>();
        Set<String> processedBatches = new HashSet<>();

        for (Job job : jobs) {
            String idBatch = job.getIdBatch();

            if (idBatch == null || !processedBatches.add(idBatch)) {
                continue;
            }

            List<CameraFactRow> rows = getResultList(
                    queries.loadCameraFact(),
                    CAMERA_FACT_MAPPING,
                    idBatch
            );

            if (!rows.isEmpty()) {
                CameraFactRow row = rows.getFirst();

                result.put(
                        idBatch,
                        new CameraValue(
                                row.cameraStart() != null ? row.cameraStart() : null,
                                row.cameraEnd() != null ? row.cameraEnd() : null
                        )
                );
            }
        }

        return result;
    }

    // ========================= CORE (JPA HELPER) =========================

    @SuppressWarnings("unchecked")
    private <T> List<T> getResultList(
            String sql,
            String mapping,
            Object... params
    ) {
        Query query = em.createNativeQuery(sql, mapping);

        for (int i = 0; i < params.length; i++) {
            query.setParameter(i + 1, params[i]);
        }

        return query.getResultList();
    }
}