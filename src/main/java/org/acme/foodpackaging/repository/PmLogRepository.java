package org.acme.foodpackaging.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.entity.jobs.PmLog;
import org.acme.foodpackaging.record.CameraFactRow;
import org.acme.foodpackaging.sql.SqlQueries;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.stream.Stream;

@ApplicationScoped
public class PmLogRepository implements PanacheRepository<PmLog> {

    private final SqlQueries sqlQueries;

    @Inject
    public PmLogRepository(SqlQueries sqlQueries) {
        this.sqlQueries = sqlQueries;
    }

    public long countByIdBatch(String idBatch) {
        Number result = (Number) getEntityManager()
                .createNativeQuery(
                        "SELECT COUNT(*) FROM [prommark].[dbo].[PM_LOG] WITH (NOLOCK) " +
                                "WHERE IDBATCH = ? AND KD = 17 AND TP = 0"
                )
                .setParameter(1, idBatch)
                .getSingleResult();

        return result.longValue();
    }

    public CameraFactRow getCameraFactRow(String idBatch) {
        return (CameraFactRow) getEntityManager()
                .createNativeQuery(
                        "SELECT MIN(DTS) as DTSTART, MAX(DTS) as DTEND " +
                                "FROM [prommark].[dbo].[PM_LOG] WITH (NOLOCK) " +
                                "WHERE IDBATCH = ? AND KD = 71",
                        "CameraFactRowMapping"
                )
                .setParameter(1, idBatch)
                .getSingleResult();
    }

    public Stream<LocalDateTime> streamMarkingDtsByIdBatch(String idBatch) {
        return getEntityManager()
                .createNativeQuery(sqlQueries.loadPmLogMarkingRowsByBatch())
                .setParameter(1, idBatch)
                .getResultStream()
                .map(PmLogRepository::extractDts)
                .filter(Objects::nonNull);
    }

    private static LocalDateTime extractDts(Object rowObj) {
        if (!(rowObj instanceof Object[] row) || row.length < 2) {
            return null;
        }
        return toDts(row[1]);
    }

    private static LocalDateTime toDts(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Timestamp t) {
            return t.toLocalDateTime();
        }
        if (o instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (o instanceof java.util.Date d) {
            return new Timestamp(d.getTime()).toLocalDateTime();
        }
        return null;
    }

}
