package org.acme.foodpackaging.persistence.load;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;
import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.record.CameraFactRow;
import org.acme.foodpackaging.record.CameraValue;
import org.acme.foodpackaging.sql.SqlQueries;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
@RequiredArgsConstructor
public class CameraDataLoader {

    @Inject
    @DataSource("prommark")
    AgroalDataSource dataSource;

    @Inject
    SqlQueries queries;

    public Map<String, CameraValue> loadCameraRowMap(Iterable<Job> jobs) {

        Map<String, CameraValue> result = new HashMap<>();
        Set<String> processedBatches = new HashSet<>();

        for (Job job : jobs) {

            String idBatch = job.getIdBatch();

            if (idBatch == null || !processedBatches.add(idBatch)) {
                continue;
            }

            CameraFactRow row = loadCameraFact(idBatch);

            if (row != null) {
                result.put(idBatch, new CameraValue(row.cameraStart(), row.cameraEnd())
                );
            }
        }

        return result;
    }

    public CameraFactRow loadCameraFact(String idBatch) {

        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(queries.loadCameraFact())) {

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
            throw new RuntimeException(
                    "Failed to load camera data for batch " + idBatch, e
            );
        }
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null
                ? null
                : timestamp.toLocalDateTime();
    }
}
