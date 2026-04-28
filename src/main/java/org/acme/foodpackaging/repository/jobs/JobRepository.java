package org.acme.foodpackaging.repository.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.persistence.load.JobDBLoader;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.dto.DbMaintenanceRow;
import org.acme.foodpackaging.record.FactKey;
import org.acme.foodpackaging.record.FactProductionRow;
import org.acme.foodpackaging.record.CameraValue;
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
    public Map<Long,DbJobRow> getDbJobRowMap(LocalDate from, LocalDate to) {
        return jobDBLoader.loadJobRowMap(
                from.atStartOfDay(), to.atStartOfDay(), ksk
        );
    }

    /**
     * Загружает список задач обслуживания из базы данных за указанный период.
     * 
     * @param from Start date (inclusive)
     * @param to End date (inclusive)
     * @return List of maintenance rows by FId
     */
    public List<DbMaintenanceRow> getMaintenanceData(LocalDate from, LocalDate to) {
        return jobDBLoader.loadMaintenanceRows(
                from.atStartOfDay(), to.atStartOfDay()
        );
    }

    /**
     * Загружает карту партий с задержкой фасовки по времени.
     *
     * @param from Start date (inclusive)
     * @param to End date (inclusive)
     * @return Map of delay rows by Event 10
     */
    public Map<Long, DbMaintenanceRow> getDelayData(LocalDate from, LocalDate to) {
        return jobDBLoader.loadDelayDurationRows(
                from.atStartOfDay(), to.atStartOfDay()
        );
    }
    /**
     * Загружает карту партий с задержкой фасовки по времени.
     *
     * @param from Start date (inclusive)
     * @param to End date (inclusive)
     * @return Map of cleaning delay rows by Event 11
     */
    public Map<Long, DbMaintenanceRow> getCleaningDelayData(LocalDate from, LocalDate to) {
        return jobDBLoader.loadCleaningDelayDurationRows(
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


