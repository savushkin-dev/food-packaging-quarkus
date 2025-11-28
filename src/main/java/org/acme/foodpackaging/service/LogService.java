package org.acme.foodpackaging.service;


import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.foodpackaging.entity.RequestLog;
import org.acme.foodpackaging.repository.RequestLogRepository;

import java.time.LocalDateTime;

@ApplicationScoped
public class LogService {

    @Inject
    RequestLogRepository requestLogRepository;

    @Transactional
    public void logRequest(String login, String method, String query) {
        RequestLog log = RequestLog.builder()
                .login(login)
                .dateTime(LocalDateTime.now())
                .method(method)
                .query(query)
                .build();

        requestLogRepository.persist(log);
    }

}
