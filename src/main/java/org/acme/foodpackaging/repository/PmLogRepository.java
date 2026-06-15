package org.acme.foodpackaging.repository;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.exception.service.CameraDataReadException;
import org.acme.foodpackaging.record.CameraFactRow;
import org.acme.foodpackaging.sql.SqlQueries;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@ApplicationScoped
public class PmLogRepository {

    private final AgroalDataSource dataSource;
    private final SqlQueries sqlQueries;

    @Inject
    public PmLogRepository(
            @DataSource("prommark") AgroalDataSource dataSource,
            SqlQueries sqlQueries) {
        this.dataSource = dataSource;
        this.sqlQueries = sqlQueries;
    }

    public long countByIdBatch(String idBatch) {

        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(sqlQueries.countPmLogByBatch())) {
            ps.setString(1, idBatch);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }

        } catch (SQLException e) {
            throw new CameraDataReadException(
                    "Failed to count PM_LOG for batch " + idBatch,
                    e);
        }
    }

    public CameraFactRow getCameraFactRow(String idBatch) {

        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(sqlQueries.loadCameraFact())) {
            ps.setString(1, idBatch);

            try (ResultSet rs = ps.executeQuery()) {

                if (!rs.next()) {
                    return null;
                }

                return new CameraFactRow(
                        rs.getObject("DTSTART", LocalDateTime.class),
                        rs.getObject("DTEND", LocalDateTime.class));
            }

        } catch (SQLException e) {
            throw new CameraDataReadException(
                    "Failed to load camera fact for batch " + idBatch,
                    e);
        }
    }

    public Stream<LocalDateTime> streamMarkingDtsByIdBatch(String idBatch) {

        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(sqlQueries.loadPmLogMarkingRowsByBatch())) {
            ps.setString(1, idBatch);

            ResultSet rs = ps.executeQuery();
            List<LocalDateTime> result = new ArrayList<>();

            while (rs.next()) {
                LocalDateTime dts = rs.getObject("DTS", LocalDateTime.class);
                if (dts != null) {
                    result.add(dts);
                }
            }

            return result.stream();

        } catch (SQLException e) {
            throw new CameraDataReadException(
                    "Failed to load marking rows for batch " + idBatch,
                    e);
        }
    }
}