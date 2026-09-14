package org.acme.foodpackaging.repository.jobs;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.entity.jobs.MsLog;

@ApplicationScoped
public class MsLogRepository implements PanacheRepository<MsLog> {

    public MsLog findByIdBatchAndEvent(String idBatch, Integer eventType) {
        return find(
                "idBatch = ?1 and eventType = ?2",
                idBatch,
                eventType
        ).firstResult();
    }
}
