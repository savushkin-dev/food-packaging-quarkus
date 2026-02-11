
package org.acme.foodpackaging.repository.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.persistence.load.JobDBLoader;
import org.acme.foodpackaging.record.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDate;
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
    public MaintenanceData getMaintenanceData(LocalDate from, LocalDate to) {
        return jobDBLoader.loadMaintenanceData(
                from.atStartOfDay(), to.atStartOfDay()
        );
    }

    /**
     * Загружает карту фактического производства.
     *
     * @param from Start date (inclusive)
     * @param to End date (inclusive)
     * @return Map of fact production rows by FactKey
     */
    public Map<FactKey, FactProductionRow> getFactProductionRowMap(LocalDate from, LocalDate to) {
        return jobDBLoader.loadFactProductionRowMap(
                from.atStartOfDay(), to.atStartOfDay()
        );
    }

    /**
     * Загружает карту фактического производства по камере.
     *
     * @param jobs list with idBatch (inclusive)
     * @return Map of camera start, camera end production rows by idBatch
     */
    public Map<String, CameraValue> getCameraFactRowMap(List<Job> jobs) {

        if (jobs.isEmpty()) {
            return Map.of();
        }

        return jobDBLoader.loadCameraRowMap(jobs);
    }
}
