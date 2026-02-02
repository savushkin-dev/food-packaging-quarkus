package org.acme.foodpackaging.persistence.upload;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.dto.PmLogInsertRow;
import org.acme.foodpackaging.exception.rest.service.DataUploadException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.*;
import java.util.*;
import java.time.LocalDateTime;

import static io.smallrye.config._private.ConfigLogging.log;
import static org.acme.foodpackaging.sql.SqlQueries.*;

@ApplicationScoped
public class UploadDataService {

    @ConfigProperty(name = "db.url")
    String dbUrl;

    @ConfigProperty(name = "ksk")
    String ksk;

    @ConfigProperty(name = "krca")
    String krca;
    /**
     * Отправляет задачи в работу (UPDATE_WORK + процедура)
     */
    public void sendToWork(List<Job> jobs) {

        List<Job> jobsToProcess = jobs.stream()
                .filter(job -> job.getSnpz() != null)
                .toList();

        if (jobsToProcess.isEmpty()) {
            log.info("No jobs with non-null snpz to process");
            return;
        }

        Connection conn = null;
        try {
            conn = DriverManager.getConnection(dbUrl);
            conn.setAutoCommit(false); // ручное управление транзакцией для атомарности

            try (PreparedStatement ps = conn.prepareStatement(UPDATE_WORK)) {
                for (Job job : jobsToProcess) {
                    ps.setString(1, job.getLine().getId());
                    ps.setObject(2, job.getStartProductionDateTime());
                    ps.setObject(3, job.getEndDateTime());
                    ps.setLong(4, job.getDuration().toMinutes());
                    ps.setLong(5, job.getSnpz());
                    ps.addBatch();
                }
                ps.executeBatch();

                try (PreparedStatement proc = conn.prepareStatement(REFRESH_FASP)) {
                    proc.setString(1, krca);
                    proc.setString(2, ksk);
                    proc.execute();
                } catch (SQLException e) {
                    e.fillInStackTrace();
                    throw e;
                }

                conn.commit(); // фиксируем транзакцию
                log.info("Successfully UPDATE_WORK");
            }
        } catch (SQLException e) {
            log.error("Error UPDATE_WORK, rollback", e);
            if (conn != null) {
                try {
                    conn.rollback(); // откатываем
                    log.warn("Rollback UPDATE_WORK");
                } catch (SQLException rollbackEx) {
                    log.error("Error rollback", rollbackEx);
                }
            }
            throw new RuntimeException("Error UPDATE_WORK, rollback", e);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                    log.debug("Close connection UPDATE_WORK");
                } catch (Exception closeEx) {
                    log.error("Error close connection UPDATE_WORK", closeEx);
                }
            }
        }
    }

    /**
     * Writes a single camera event (2 = start, 3 = end) into MS_LOG.
     */
    public void writeCameraEvent(PmLogInsertRow insertRow) {
        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            try (PreparedStatement ps = conn.prepareStatement(INSERT_CAMERA_EVENT)) {
                // (IDBATCH, KMC, DTV, NP, EVENT, DT, KRC)
                ps.setString(1, insertRow.getIdBatch());
                ps.setString(2, insertRow.getProductId());
                ps.setTimestamp(3, Timestamp.valueOf(insertRow.getDtv()));
                if (insertRow.getNp() == null) ps.setNull(4, Types.INTEGER); else ps.setInt(4, insertRow.getNp());
                ps.setInt(5, insertRow.getEventType());
                ps.setTimestamp(6, Timestamp.valueOf(insertRow.getEventTime()));
                ps.setString(7, insertRow.getLineId());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            log.error("Failed to write camera event", e);
            throw new DataUploadException("Failed to write camera event", e);
        }
    }

    /**
     * Batch-inserts camera events to MS_LOG for efficiency.
     * startEvents: idBatch -> start time (eventType=2)
     * endEvents:   idBatch -> end time   (eventType=3)
     */
    public void writeCameraEventsBatchRows(Map<String, PmLogInsertRow> startEvents, Map<String, PmLogInsertRow> endEvents) {

        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            conn.setAutoCommit(false);
            writeCameraEventsBatchRowsInternal(conn, startEvents, endEvents);
            conn.commit();
        } catch (SQLException e) {
            log.error("Failed to batch write camera events", e);
            throw new DataUploadException("Failed to batch write camera events", e);
        }
    }

    /**
     * Extracted internal writer for batch events to reduce nesting.
     */
    private void writeCameraEventsBatchRowsInternal(Connection conn,
                                                    Map<String, PmLogInsertRow> startEvents,
                                                    Map<String, PmLogInsertRow> endEvents
                                                   ) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_CAMERA_EVENT)) {
            if (startEvents != null && !startEvents.isEmpty()) {
                for (var entry : startEvents.entrySet()) {
                    if (entry.getValue() == null) continue;
                    addPmLogInsertRow(ps, entry.getValue());
                }
            }
            if (endEvents != null && !endEvents.isEmpty()) {
                for (var entry : endEvents.entrySet()) {
                    if (entry.getValue() == null) continue;
                    addPmLogInsertRow(ps, entry.getValue());
                }
            }
            ps.executeBatch();
        }
    }

    /**
     * Batch update DT for EVENT=3 for many batches in a single transaction.
     */
    public int updateEvent3ForBatches(Map<String, java.time.LocalDateTime> endByBatch) {
        if (endByBatch == null || endByBatch.isEmpty()) return 0;
        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            conn.setAutoCommit(false);
            int updated = 0;
            try (PreparedStatement ps = conn.prepareStatement(UPDATE_MS_LOG_EVENT3_DT)) {
                for (var e : endByBatch.entrySet()) {
                    if (e.getKey() == null || e.getValue() == null) continue;
                    ps.setTimestamp(1, Timestamp.valueOf(e.getValue()));
                    ps.setString(2, e.getKey());
                    ps.addBatch();
                }
                int[] counts = ps.executeBatch();
                for (int c : counts) if (c > 0) updated += c;
            }
            conn.commit();
            return updated;
        } catch (SQLException e) {
            log.error("Failed to batch update EVENT=3 DT", e);
            throw new DataUploadException("Failed to batch update EVENT=3 DT", e);
        }
    }

    private static void addPmLogInsertRow(PreparedStatement ps, PmLogInsertRow insertRow) throws SQLException {
        // (IDBATCH, KMC, DTV, NP, EVENT, DT, KRC)
        ps.setString(1, insertRow.getIdBatch());
        ps.setString(2, insertRow.getProductId());
        ps.setTimestamp(3, Timestamp.valueOf(insertRow.getDtv()));
        if (insertRow.getNp() == null) {
            ps.setNull(4, Types.INTEGER);
        } else {
            ps.setInt(4, insertRow.getNp());
        }
        ps.setInt(5, insertRow.getEventType());
        ps.setTimestamp(6, Timestamp.valueOf(insertRow.getEventTime()));
        ps.setString(7, insertRow.getLineId());
        ps.addBatch();
    }

    /**
     * Update DT for EVENT=3 in MS_LOG for a specific batch.
     * Returns the number of updated rows (0 or 1).
     */
    public int updateEvent3ForBatch(String idBatch, LocalDateTime endTime) {
        if (idBatch == null || endTime == null) return 0;
        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            try (PreparedStatement ps = conn.prepareStatement(UPDATE_MS_LOG_EVENT3_DT)) {
                ps.setTimestamp(1, Timestamp.valueOf(endTime));
                ps.setString(2, idBatch);
                return ps.executeUpdate();
            }
        } catch (SQLException e) {
            log.error("Failed to update EVENT=3 DT for " + idBatch, e);
            throw new DataUploadException("Failed to update EVENT=3 DT", e);
        }
    }
}

