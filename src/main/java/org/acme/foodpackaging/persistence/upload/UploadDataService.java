package org.acme.foodpackaging.persistence.upload;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.acme.foodpackaging.domain.Job;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.*;
import java.util.List;

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
     * Фильтрует сервисные операции перед обработкой
     */
    @Transactional
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
                for (Job job : jobs) {
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
}