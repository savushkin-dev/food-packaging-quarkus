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
    public void logRequest(String login, String ip, String method, String query) {

        query = trimToColumnLength(query, 7000);

        RequestLog log = RequestLog.builder()
                .login(login)
                .dateTime(LocalDateTime.now())
                .ip(ip)
                .method(method)
                .query(query)
                .build();

        requestLogRepository.persist(log);
    }

    public String trimToColumnLength(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() > maxLength) {
            return value.substring(0, maxLength);
        }
        return value;
    }

}
