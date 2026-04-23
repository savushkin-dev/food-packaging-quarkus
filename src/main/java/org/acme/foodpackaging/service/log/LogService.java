package org.acme.foodpackaging.service.log;

import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.container.ContainerRequestContext;
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

    public String getIp(ContainerRequestContext requestContext, HttpServerRequest vertxRequest) {
        String ip = requestContext.getHeaderString("X-Real-IP");

        if (ip == null || ip.isBlank()) {
            ip = requestContext.getHeaderString("X-Forwarded-For");
        }

        if (ip == null || ip.isBlank()) {
            ip = vertxRequest.remoteAddress().host();
        }

        return (ip != null && !ip.isBlank()) ? ip : "unknown";
    }


    public String getLoginIdentifier(String username, String sessionId, String defaultSessionId) {

        if (username != null && !username.isBlank()) {
            return username;
        }

        if (sessionId != null && !sessionId.isBlank() && !defaultSessionId.equalsIgnoreCase(sessionId)) {
            return sessionId;
        }

        return defaultSessionId;
    }

}
