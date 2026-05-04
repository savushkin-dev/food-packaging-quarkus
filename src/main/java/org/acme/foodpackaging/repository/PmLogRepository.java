package org.acme.foodpackaging.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.foodpackaging.entity.jobs.PmLog;
import org.acme.foodpackaging.record.CameraFactRow;
import org.acme.foodpackaging.record.PmLogMarkingRow;
import org.acme.foodpackaging.sql.SqlQueries;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class PmLogRepository implements PanacheRepository<PmLog> {

    @Inject
    SqlQueries sqlQueries;

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

    @SuppressWarnings("unchecked")
    public List<PmLogMarkingRow> findMarkingRowsByIdBatch(String idBatch) {
        List<Object[]> raw = getEntityManager()
                .createNativeQuery(sqlQueries.loadPmLogMarkingRowsByBatch())
                .setParameter(1, idBatch)
                .getResultList();

        List<PmLogMarkingRow> out = new ArrayList<>(raw.size());
        for (Object[] row : raw) {
            if (row == null || row.length < 2) {
                continue;
            }
            LocalDateTime dts = toDts(row[1]);
            if (dts == null) {
                continue;
            }
            out.add(new PmLogMarkingRow(toFId(row[0]), dts));
        }
        return out;
    }

    private static long toFId(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        if (o instanceof String s) {
            return Long.parseLong(s.trim());
        }
        throw new IllegalArgumentException("Unexpected F_ID type: " + (o == null ? "null" : o.getClass().getName()));
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
