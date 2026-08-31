package org.acme.foodpackaging.persistence.load;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.dto.oeepev.CleaningRow;
import org.acme.foodpackaging.dto.oeepev.DelayRow;
import org.acme.foodpackaging.dto.oeepev.MaintenanceRow;
import org.acme.foodpackaging.persistence.constants.DelayEventType;
import org.acme.foodpackaging.dto.bdvzpmc.JobRow;
import org.acme.foodpackaging.record.FactKey;
import org.acme.foodpackaging.record.FactProductionRow;
import org.acme.foodpackaging.sql.SqlQueries;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class JobDBLoader {

    private static final String DB_JOB_ROW_MAPPING = "JobRowMapping";
    private static final String FACT_PRODUCTION_MAPPING = "FactProductionRowMapping";

    private final EntityManager em;
    private final SqlQueries queries;

    private <K, T> Map<K, T> toMapByKey(List<T> rows, Function<T, K> keyExtractor) {
        Map<K, T> result = HashMap.newHashMap(rows.size());
        for (T row : rows) {
            result.putIfAbsent(keyExtractor.apply(row), row);
        }
        return result;
    }

    // ========================= JOBS =========================

    public Map<Long, JobRow> loadJobRowMap(LocalDateTime from, LocalDateTime to, String ksk) {
        List<JobRow> rows = getResultList(queries.loadJobs(), DB_JOB_ROW_MAPPING, from, to, ksk, from);
        return toMapByKey(rows, JobRow::snpz);
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
    public Map<Long, CleaningRow> loadCleaningRows(LocalDateTime from, LocalDateTime to) {
        List<CleaningRow> rows = getResultList(queries.loadCleaningData(), "CleaningRowMapping", from, to);
        return toMapByKey(rows, CleaningRow::snpz);
    }


    // packaging/cleaning delays
    public Map<Long, DelayRow> loadDelayRowsByType(DelayEventType eventType, LocalDateTime from, LocalDateTime to) {
        List<DelayRow> rows = getResultList(queries.loadDelayData(eventType.code()), "DelayRowMapping", from, to);
        return toMapByKey(rows, DelayRow::snpz);
    }

    // ========================= FACT =========================

    public Map<FactKey, FactProductionRow> loadFactProductionRowMap(LocalDateTime from, LocalDateTime to) {
        List<FactProductionRow> rows = getResultList(queries.loadFact(), FACT_PRODUCTION_MAPPING, from, to);
        return toMapByKey(rows, row -> new FactKey(row.kmc(), row.np(), row.eventType()));
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