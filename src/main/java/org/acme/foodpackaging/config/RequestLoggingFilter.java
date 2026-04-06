package org.acme.foodpackaging.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.exception.rest.RequestBodyReadException;
import org.acme.foodpackaging.service.log.LogService;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.calculateDownTime;

@Provider
@Priority(2)
public class RequestLoggingFilter implements ContainerRequestFilter {

    @Inject
    public RequestLoggingFilter(HttpServerRequest vertxRequest, LogService logService, ObjectMapper objectMapper){
        this.vertxRequest = vertxRequest;
        this.logService = logService;
        this.objectMapper = objectMapper;
    }

    private final HttpServerRequest vertxRequest;
    private final LogService logService;
    private final ObjectMapper objectMapper;

    private static final String DEFAULT_SESSION_ID = "default_session_id";

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();
        String login = requestContext.getHeaderString("X-Session-Id");
        String method = requestContext.getMethod();

        if (login == null || login.isBlank() || DEFAULT_SESSION_ID.equalsIgnoreCase(login)
        || "GET".equalsIgnoreCase(method)) {
            return;
        }

        InputStream entityStream = requestContext.getEntityStream();
        String body;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(entityStream, StandardCharsets.UTF_8))) {
            body = reader.lines()
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read request body", e);
        }

        requestContext.setEntityStream(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));

        String endpoint = path.contains("/")
                ? path.substring(path.lastIndexOf('/') + 1)
                : path;

        String ip = getIp(requestContext);

        String extraJson = null;

        if ("POST".equalsIgnoreCase(method) && (endpoint.equals("save") || endpoint.equals("stopSolving"))) {
            extraJson = generateDowntimeJson(body);
        }

        logService.logRequest(login, ip, path.substring(path.lastIndexOf('/') + 1), body);
    }

    private String readRequestBody(ContainerRequestContext requestContext) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(requestContext.getEntityStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RequestBodyReadException("Failed to read request body", e);
        }
    }

    private String extractEndpoint(String path) {
        if (path == null || path.isBlank()) {
            return "unknown";
        }
        return path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
    }

    private String generateDowntimeJson(String body) {
        try {
            PackagingSchedule solution = objectMapper.readValue(body, PackagingSchedule.class);
            LocalDate dti = solution.getWorkCalendar().getCurrentDate();
            long downtimeMinutes = calculateDownTime(solution).toMinutes();

            Map<String, Object> payload = new HashMap<>();
            payload.put("dti", dti.toString());
            payload.put("downtime", downtimeMinutes);

            return objectMapper.writeValueAsString(payload);

        } catch (Exception e) {
            return "{\"error\":\"failed to calculate downtime\"}";
        }
    }

    public String getIp(ContainerRequestContext requestContext){
        // сначала пробуем заголовки от Nginx
        String ip = requestContext.getHeaderString("X-Real-IP");
        if (ip == null || ip.isBlank()) {
            ip = requestContext.getHeaderString("X-Forwarded-For");
        }

        // если заголовков нет — берём реальный адрес
        if (ip == null || ip.isBlank()) {
            ip = vertxRequest.remoteAddress().host();
        }

        if (ip != null && !ip.isBlank()) {
            return ip;
        } else {
            return "unknown";
        }
    }
}
