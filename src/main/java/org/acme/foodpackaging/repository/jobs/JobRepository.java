package org.acme.foodpackaging.repository.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.dto.row.maintenance.CleaningRow;
import org.acme.foodpackaging.dto.row.maintenance.DelayRow;
import org.acme.foodpackaging.dto.row.maintenance.MaintenanceRow;
import org.acme.foodpackaging.exception.service.CameraDataReadException;
import org.acme.foodpackaging.repository.PmLogRepository;
import org.acme.foodpackaging.service.load.DelayEventType;
import org.acme.foodpackaging.service.load.JobDBLoader;
import org.acme.foodpackaging.dto.row.jobs.JobRow;
import org.acme.foodpackaging.domain.value.FactKey;
import org.acme.foodpackaging.dto.row.jobs.FactProductionRow;
import org.acme.foodpackaging.dto.row.jobs.CameraFactRow;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Data access repository for jobs.
 * Handles loading job and maintenance data from the database.
 */
@ApplicationScoped
public class JobRepository {

    @Inject
    public JobRepository(JobDBLoader jobDBLoader, PmLogRepository pmLogRepository) {
        this.jobDBLoader = jobDBLoader;
        this.pmLogRepository = pmLogRepository;
    }

    private final JobDBLoader jobDBLoader;
    private final PmLogRepository pmLogRepository;

    @ConfigProperty(name = "ksk")
    String ksk;

    /**
     * Загружает карту задач из базы данных за указанный период.
     *
     * @param from Start date (inclusive)
     * @param to   End date (inclusive)
     * @return Map of job rows by SNPZ
     */
    public Map<Long, JobRow> getJobRowMap(LocalDate from, LocalDate to) {
        return jobDBLoader.loadJobRowMap(
                from.atStartOfDay(), to.atStartOfDay(), ksk);
    }

    /**
     * Загружает список задач обслуживания из базы данных за указанный период.
     *
     * @param from Start date (inclusive)
     * @param to   End date (inclusive)
     * @return List of maintenance rows by FId
     */
    public List<MaintenanceRow> getMaintenanceData(LocalDate from, LocalDate to) {
        return jobDBLoader.loadMaintenanceRows(
                from.atStartOfDay(), to.atStartOfDay());
    }

    /**
     * Загружает карту c fid для cleaning.
     *
     * @param from Start date (inclusive)
     * @param to   End date (inclusive)
     * @return Map of delay rows by Event 10
     */

    public Map<Long, CleaningRow> getCleaningData(LocalDate from, LocalDate to) {
        return jobDBLoader.loadCleaningRows(
                from.atStartOfDay(), to.atStartOfDay());
    }

    /**
     * Загружает карту партий с задержкой фасовки по времени.
     *
     * @param from Start date (inclusive)
     * @param to   End date (inclusive)
     * @return Map of delay rows by Event 10
     */
    public Map<Long, DelayRow> loadDelayDurationRows(LocalDate from, LocalDate to) {
        return jobDBLoader.loadDelayRowsByType(DelayEventType.PACKAGING, from.atStartOfDay(), to.atStartOfDay());
    }

    /**
     * Загружает карту партий с задержкой фасовки по времени.
     *
     * @param from Start date (inclusive)
     * @param to   End date (inclusive)
     * @return Map of cleaning delay rows by Event 11
     */
    public Map<Long, DelayRow> loadCleaningDelayDurationRows(LocalDate from, LocalDate to) {
        return jobDBLoader.loadDelayRowsByType(DelayEventType.CLEANING, from.atStartOfDay(), to.atStartOfDay());
    }

    /**
     * Загружает карту фактического производства.
     *
     * @param from Start date (inclusive)
     * @param to   End date (inclusive)
     * @return Map of fact production rows by FactKey
     */
    public Map<FactKey, FactProductionRow> getFactProductionRowMap(LocalDate from, LocalDate to) {
        return jobDBLoader.loadFactProductionRowMap(
                from.atStartOfDay(), to.atStartOfDay());
    }

    /**
     * Загружает карту фактического производства по камере.
     *
     * @param jobs list with idBatch (inclusive)
     * @return Map of camera start, camera end production rows by idBatch
     */
    public Map<String, CameraFactRow> getCameraFactRowMap(List<Job> jobs) throws CameraDataReadException {

        if (jobs.isEmpty()) {
            return Map.of();
        }

        Map<String, CameraFactRow> result = HashMap.newHashMap(jobs.size());

        for (String idBatch : jobs.stream()
                .map(Job::getIdBatch)
                .filter(Objects::nonNull)
                .distinct()
                .toList()) {

            CameraFactRow row = pmLogRepository.getCameraFactRow(idBatch);
            if (row != null) {
                result.put(idBatch, row);
            }
        }

        return result;
    }
}
