package org.acme.foodpackaging.repository;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.record.CameraFactRow;
import org.acme.foodpackaging.sql.SqlQueries;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@ApplicationScoped
@RequiredArgsConstructor
public class PmLogRepository {

    @Inject
    @DataSource("prommark")
    AgroalDataSource dataSource;

    private final SqlQueries sqlQueries;

    public long countByIdBatch(String idBatch) {
        String sql = """
                SELECT COUNT(*)
                FROM dbo.PM_LOG WITH (NOLOCK)
                WHERE IDBATCH = ?
                  AND KD = 17
                  AND TP = 0
                """;

        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)
        ) {
            ps.setString(1, idBatch);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count PM_LOG rows for batch " + idBatch, e);
        }
    }

    public CameraFactRow getCameraFactRow(String idBatch) {
        String sql = """
                SELECT MIN(DTS) AS DTSTART,
                       MAX(DTS) AS DTEND
                FROM dbo.PM_LOG WITH (NOLOCK)
                WHERE IDBATCH = ?
                  AND KD = 71
                """;

        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)
        ) {
            ps.setString(1, idBatch);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                return new CameraFactRow(
                        toLocalDateTime(rs.getTimestamp("DTSTART")),
                        toLocalDateTime(rs.getTimestamp("DTEND"))
                );
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load camera facts for batch " + idBatch, e);
        }
    }

    public Stream<LocalDateTime> streamMarkingDtsByIdBatch(String idBatch) {
        List<LocalDateTime> result = new ArrayList<>();

        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(
                        sqlQueries.loadPmLogMarkingRowsByBatch()
                )
        ) {
            ps.setString(1, idBatch);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDateTime dts = toLocalDateTime(rs.getTimestamp(2));

                    if (dts != null) {
                        result.add(dts);
                    }
                }
            }

            return result.stream();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load marking rows for batch " + idBatch, e);
        }
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null
                ? null
                : timestamp.toLocalDateTime();
    }
}
