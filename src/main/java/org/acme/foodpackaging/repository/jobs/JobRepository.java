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

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

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

    public  Map<String, CameraValue> getCameraValueMap(Set<String> batchIds) {
        return jobDBLoader.loadCameraValuesFromPmLogJDBC( batchIds);
    }

    public Map<String, LocalDateTime> getCameraUpdate(Set<String> batches){
        return jobDBLoader.loadActualCameraEndFromPmLog( batches);
    }
}


