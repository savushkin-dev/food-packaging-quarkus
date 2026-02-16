package org.acme.foodpackaging.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.entity.jobs.PmLog;
import org.acme.foodpackaging.record.CameraFactRow;

@ApplicationScoped
public class PmLogRepository implements PanacheRepository<PmLog> {

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

}
