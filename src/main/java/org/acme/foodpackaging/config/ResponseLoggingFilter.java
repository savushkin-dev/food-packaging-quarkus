package org.acme.foodpackaging.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.acme.foodpackaging.service.log.LogService;

import java.util.Set;

@Provider
@Priority(2)
public class ResponseLoggingFilter implements ContainerResponseFilter {

    @Inject
    HttpServerRequest vertxRequest;
    @Inject
    LogService logService;
    @Inject
    ObjectMapper objectMapper;

    private static final Set<String> ENDPOINTS_TO_LOG = Set.of("save", "stopSolving");
    private static final String DEFAULT_SESSION_ID = "default_session_id";

    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) {

        String path = requestContext.getUriInfo().getPath();
        String endpoint = extractEndpoint(path);

        if (!ENDPOINTS_TO_LOG.contains(endpoint)) {
            return;
        }

        String sessionId = requestContext.getHeaderString("X-Session-Id");
        String username = requestContext.getHeaderString("X-Username");

        // Определяем идентификатор для логирования (Приоритет: X-Username > X-Session-Id > default)
        String login = logService.getLoginIdentifier(username, sessionId, DEFAULT_SESSION_ID);

        String ip = logService.getIp(requestContext, vertxRequest);

        Object entity = responseContext.getEntity();
        String response;

        try {
            response = (entity != null) ? objectMapper.writeValueAsString(entity) : "";
        } catch (Exception e) {
            response = "{\"error\":\"failed to serialize response\"}";
        }

        logService.logRequest(login, ip, endpoint, response);
    }

    private String extractEndpoint(String path) {
        if (path == null || path.isBlank()) {
            return "unknown";
        }
        return path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
    }


}
