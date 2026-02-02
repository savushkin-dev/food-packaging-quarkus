package org.acme.foodpackaging.repository.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.persistence.load.JobDBLoader;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.dto.DbMaintenanceRow;
import org.acme.foodpackaging.record.FactProductionRow;
import org.acme.foodpackaging.record.CameraValue;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Data access repository for jobs.
 * Handles loading job and maintenance data from the database.
 */
@ApplicationScoped
public class JobRepository {

    @Inject
    JobDBLoader jobDBLoader;

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

}


