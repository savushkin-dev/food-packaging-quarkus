package org.acme.foodpackaging.persistence.upload;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.persistence.scheduleSaving.SolutionExport;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.*;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static io.smallrye.config._private.ConfigLogging.log;
import static org.acme.foodpackaging.sql.SqlQueries.*;

@ApplicationScoped
public class UploadDataService {

    @Inject
    SolutionExport solutionExport;
    @ConfigProperty(name = "db.url")
    String dbUrl;
    /**
     * Сохраняет расписание (packagingSchedule) в таблицу JSON
     */
    public void saveSchedule(PackagingSchedule schedule) {
        try {
           solutionExport.export(schedule);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save schedule", e);
        }
    }
    /**
     * Удаляет расписание по дате
     */
    public int deleteSchedule(LocalDate date) {
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement stmt = conn.prepareStatement(DELETE_SOLUTION_JSON)) {

            stmt.setDate(1, java.sql.Date.valueOf(date));
            return stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete schedule: " + date, e);
        }
    }
    /**
     * Загружает партии PLR_PDAYNP + BD_VZPMC
     */
    public Map<String, Map<String, Object>> loadPDay(LocalDate startDate, LocalDate endDate) {
        Map<String, Map<String, Object>> result = new TreeMap<>();

        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement ps = conn.prepareStatement(LOAD_PDAY)) {

            ps.setString(1, startDate + "T00:00:00");
            ps.setString(2, endDate + "T00:00:00");
            ps.setString(3, "0119030000");

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                String snpz = rs.getString(8);

                row.put("SNM", rs.getString(1));
                row.put("KMC", rs.getString(2));
                row.put("DTI", rs.getDate(3));
                row.put("DTF", rs.getDate(4));
                row.put("NP", rs.getInt(5));
                row.put("KOLEV", rs.getInt(6));
                row.put("UX", rs.getInt(7));
                row.put("SNPZ", rs.getInt(8));
                row.put("MASSA", rs.getInt(9));

                result.put(snpz, row);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load PLR_PDAYNP", e);
        }

        // BD_VZPMC + INSERT
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement ps = conn.prepareStatement(LOAD_VZPMC);
             PreparedStatement stmt = conn.prepareStatement(INSERT_PDAY)) {

            ps.setString(1, startDate + "T00:00:00");
            ps.setString(2, endDate + "T00:00:00");
            ps.setString(3, "0119030000");
            ps.setDouble(4, 0.1);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                String snpz = rs.getString(8);

                if (!result.containsKey(snpz)) {
                    stmt.setInt(1, rs.getInt(8));       // SNPZ
                    stmt.setString(2, "0119030000");   // KSK
                    stmt.setString(3, rs.getString(2)); // KMC
                    stmt.setDate(4, rs.getDate(3));
                    stmt.setInt(5, rs.getInt(5));
                    stmt.setInt(6, rs.getInt(6));
                    stmt.setInt(7, rs.getInt(7));
                    stmt.setInt(8, rs.getInt(8));
                    stmt.setInt(9, rs.getInt(9));
                    stmt.executeUpdate();
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load BD_VZPMC", e);
        }

        return result;
    }
    /**
     * Обновляет время окончания партий
     */
    public void updatePDay(Map<String, LocalDate> mapsnpz) {
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement stmt = conn.prepareStatement(UPDATE_PDAYDTF)) {

            for (Map.Entry<String, LocalDate> entry : mapsnpz.entrySet()) {
                stmt.setDate(1, java.sql.Date.valueOf(entry.getValue()));
                stmt.setString(2, entry.getKey());
                stmt.addBatch();
            }

            stmt.executeBatch();
            log.info("Successfully UPDATE_PDAYDTF");
        } catch (SQLException e) {
            log.error("Error UPDATE_PDAYDTF", e);
            throw new RuntimeException("Failed to update jobs to PLR_PDAYNP " + e.getMessage(), e);
        }
    }
    /**
     * Отправляет задачи в работу (UPDATE_WORK + процедура)
     */
    public void sendToWork(List<Job> jobs) {

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
                    ps.setInt(5, job.getSnpz());
                    ps.addBatch();
                }
                ps.executeBatch();

                try (PreparedStatement proc = conn.prepareStatement(REFRESH_FASP)) {
                    proc.setString(1, "6000000");
                    proc.setString(2, "0119030000");
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

