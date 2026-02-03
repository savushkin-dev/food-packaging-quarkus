package org.acme.foodpackaging.repository.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.persistence.load.JobDBLoader;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.dto.DbMaintenanceRow;
import org.acme.foodpackaging.record.FactProductionRow;
import org.acme.foodpackaging.record.CameraValue;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.persistence.EntityManager;

import static org.acme.foodpackaging.sql.SqlQueries.LOAD_CAMERA_FACT;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data access repository for jobs.
 * Handles loading job and maintenance data from the database.
 */
@ApplicationScoped
public class JobRepository {

    @Inject
    JobDBLoader jobDBLoader;

    @Inject
    EntityManager em;

    @ConfigProperty(name = "ksk")
    String ksk;

    /**
     * Загружает карту задач из базы данных за указанный период.
     *
     * @param from Start date (inclusive)
     * @param to End date (inclusive)
     * @return Map of job rows by SNPZ
     */
    public Map<Long, DbJobRow> getDbJobRowMap(LocalDate from, LocalDate to) {
        return jobDBLoader.loadJobRowMap(
                from.atStartOfDay(), to.atStartOfDay(), ksk
        );
    }

    /**
     * Загружает карту задач обслуживания из базы данных за указанный период.
     *
     * @param from Start date (inclusive)
     * @param to End date (inclusive)
     * @return Map of maintenance rows by FId
     */
    public Map<Long, DbMaintenanceRow> getDbMaintenanceRowMap(LocalDate from, LocalDate to) {
        return jobDBLoader.loadMaintenanceRowMap(
                from.atStartOfDay(), to.atStartOfDay()
        );
    }

    public List<FactProductionRow> getMsLogEvents(LocalDate from, LocalDate to) {
        return jobDBLoader.loadMsLogEvents(from.atStartOfDay(), to.atStartOfDay());
    }

    public CameraValue getCameraValueByIdBatch(String idBatch) {
        return jobDBLoader.getCameraValueByIdBatch(idBatch);
    }

    public Map<String, CameraValue> loadCameraValuesFromPmLog(Collection<String> batchIds) {
        if (batchIds == null || batchIds.isEmpty()) {
            return Map.of();
        }
    
        List<String> ids = new ArrayList<>(batchIds);
    
        // SQL с IN (:ids)
        String sql = """
            SELECT
                IDBATCH,
                MIN(DTS) AS DTSTART,
                MAX(DTS) AS DTEND
            FROM [prommark].[dbo].[PM_LOG] WITH(NOLOCK)
            WHERE KD = 71
              AND IDBATCH IN (:ids)
            GROUP BY IDBATCH
        """;
    
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("ids", ids)
                .getResultList();
    
        Map<String, CameraValue> result = new HashMap<>();
        for (Object[] row : rows) {
            String idBatch = (String) row[0];
            LocalDateTime start = row[1] != null ? ((Timestamp) row[1]).toLocalDateTime() : null;
            LocalDateTime end = row[2] != null ? ((Timestamp) row[2]).toLocalDateTime() : null;
            result.put(idBatch, new CameraValue(start, end));
        }
    
        return result;
    }

}


