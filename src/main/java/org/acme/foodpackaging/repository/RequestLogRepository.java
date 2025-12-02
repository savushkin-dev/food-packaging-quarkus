package org.acme.foodpackaging.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.foodpackaging.entity.RequestLog;

@ApplicationScoped
public class RequestLogRepository implements PanacheRepository<RequestLog> {

}
