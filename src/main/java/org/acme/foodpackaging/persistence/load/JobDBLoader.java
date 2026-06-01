package org.acme.foodpackaging.persistence.load;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.dto.oeepev.CleaningRow;
import org.acme.foodpackaging.dto.oeepev.DelayRow;
import org.acme.foodpackaging.dto.oeepev.MaintenanceRow;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.record.FactKey;
import org.acme.foodpackaging.record.FactProductionRow;
import org.acme.foodpackaging.record.CameraFactRow;
import org.acme.foodpackaging.record.CameraValue;
import org.acme.foodpackaging.sql.SqlQueries;

import java.time.LocalDateTime;
import java.util.*;

@ApplicationScoped
public class JobDBLoader {

    private static final String DB_JOB_ROW_MAPPING = "DbJobRowMapping";
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


        Map<Long, DbJobRow> result = HashMap.newHashMap(rows.size());
        for (DbJobRow row : rows) {
            result.putIfAbsent(row.snpz(), row);
        }

        return result;
    }

    // ========================= MAINTENANCE / DELAYS =========================

    public List<MaintenanceRow> loadMaintenanceRows(
            LocalDateTime from,
            LocalDateTime to
    ) {
        return getResultList(
                queries.loadMaintenanceData(),
                "MaintenanceRowMapping",
                from, to,
                from, to
        );
    }

    // cleanings fid by snpz
    public Map<Long, CleaningRow> loadCleaningRows(
            LocalDateTime from,
            LocalDateTime to
    ) {

        List<CleaningRow> rows = getResultList(
                queries.loadCleaningData(),
                "CleaningRowMapping",
                from,
                to
        );

        Map<Long, CleaningRow> result = HashMap.newHashMap(rows.size());

        for (CleaningRow row : rows) {
            result.putIfAbsent(row.snpz(), row);
        }

        return result;
    }

// packaging/cleaning delays
    public Map<Long, DelayRow> loadDelayRowsByType(
            int eventType,
            LocalDateTime from,
            LocalDateTime to
    ) {

        List<DelayRow> rows = getResultList(
                queries.loadDelayData(eventType),
                "DelayRowMapping",
                from,
                to
        );

        Map<Long, DelayRow> result = HashMap.newHashMap(rows.size());

        for (DelayRow row : rows) {
            result.putIfAbsent(row.snpz(), row);
        }

        return result;
    }

    // ========================= FACT =========================

    public Map<FactKey, FactProductionRow> loadFactProductionRowMap(
            LocalDateTime from, LocalDateTime to
    ) {

        List<FactProductionRow> rows = getResultList(
                queries.loadFact(),
                FACT_PRODUCTION_MAPPING,
                from, to
        );

        Map<FactKey, FactProductionRow> result = HashMap.newHashMap(rows.size());

        for (FactProductionRow row : rows) {

            FactKey key = new FactKey(
                    row.kmc(), row.np(), row.eventType()
            );

            result.putIfAbsent(key, row);
        }

        return result;
    }


    // ========================= CAMERA =========================

    public Map<String, CameraValue> loadCameraRowMap(List<Job> jobs) {

        Map<String, CameraValue> result = HashMap.newHashMap(jobs.size());
        Set<String> processedBatches = HashSet.newHashSet(jobs.size());

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
                            row.cameraStart(), row.cameraEnd()
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