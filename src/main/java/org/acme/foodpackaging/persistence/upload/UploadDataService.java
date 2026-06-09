package org.acme.foodpackaging.persistence.upload;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.exception.service.DataUploadException;
import org.acme.foodpackaging.rest.ApiFields;
import org.acme.foodpackaging.sql.SqlQueries;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.acme.foodpackaging.dto.MsLogInsertRow;
import jakarta.transaction.Transactional;

import java.sql.*;
import java.util.List;

import static io.smallrye.config._private.ConfigLogging.log;

@ApplicationScoped
public class UploadDataService {

    @ConfigProperty(name = "db.url")
    String dbUrl;

    @ConfigProperty(name = "ksk")
    String ksk;

    @ConfigProperty(name = "krca")
    String krca;

    private final SqlQueries queries;

    @Inject
    public UploadDataService(SqlQueries queries) {
        this.queries = queries;
    }

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

            try (PreparedStatement ps = conn.prepareStatement(queries.updateWork())) {
                for (Job job : jobsToProcess) {
                    ps.setString(1, job.getLine().getId());
                    ps.setObject(2, job.getStartProductionDateTime());
                    ps.setObject(3, job.getEndDateTime());
                    ps.setLong(4, job.getDuration().toMinutes());
                    ps.setLong(5, job.getSnpz());
                    ps.addBatch();
                }
                ps.executeBatch();

                executeRefreshFasp(conn);

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

    private void executeRefreshFasp(Connection conn) throws SQLException {
        try (PreparedStatement proc = conn.prepareStatement(queries.refreshFasp())) {
            proc.setString(1, krca);
            proc.setString(2, ksk);
            proc.execute();
        } catch (SQLException e) {
            e.fillInStackTrace();
            throw e;
        }
    }
      /**
     * Заполняет таблицу MS_LOG данными по камере.
     * 
     * @param rows список записей для вставки в MS_LOG
     */  
    @Transactional
    public void fillMsLogTable(List<MsLogInsertRow> rows) {
    if (rows.isEmpty()) return;

    try (Connection conn = DriverManager.getConnection(dbUrl);
            PreparedStatement ps = conn.prepareStatement(queries.insertCameraEvent())) {

        for (MsLogInsertRow row : rows) {
            ps.setString(1, row.getIdBatch());
            ps.setString(2, row.getProductId());
            ps.setString(3, row.getLineIdFact());
            ps.setInt(4, row.getNp());
            ps.setInt(5, row.getEventType());
            ps.setObject(6, row.getDtv());
            ps.setObject(7, row.getEventTime());

            ps.addBatch();
        }

        ps.executeBatch();
    } catch (SQLException e) {
        throw new DataUploadException(ApiFields.MS_LOG_INSERT_FAILED, e);
    }
 }

 /**
    * Обновляет устаревшие данные по камере в таблице MS_LOG.
    * @param rows список записей для обновления данных по камере
    */

   public void updateCameraEndInMsLog(List<MsLogInsertRow> rows) {

        if (rows.isEmpty()) return;

        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement ps = conn.prepareStatement(queries.updateCameraEndEvent())) {

            for (MsLogInsertRow row : rows) {
                ps.setObject(1, row.getEventTime());
                ps.setString(2, row.getIdBatch());
                ps.setInt(3, row.getEventType());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new DataUploadException(ApiFields.MS_LOG_UPDATE_CAMERA_END_FAILED, e);
        }
    }
}


