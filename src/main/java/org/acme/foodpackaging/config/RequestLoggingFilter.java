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
import org.acme.foodpackaging.record.DownTimeData;
import org.acme.foodpackaging.service.log.LogService;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils.calculateDownTime;

@Provider
@Priority(2)
public class RequestLoggingFilter implements ContainerRequestFilter {

    @Inject
    HttpServerRequest vertxRequest;
    @Inject
    LogService logService;
    @Inject
    ObjectMapper objectMapper;

    private static final String DEFAULT_SESSION_ID = "default_session_id";

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();
        String login = requestContext.getHeaderString("X-Session-Id");
        String method = requestContext.getMethod();

        if (shouldSkip(login, method)) {
            return;
        }

        String body = readRequestBody(requestContext);
        resetEntityStream(requestContext, body);

        String ip = getIp(requestContext);
        String endpoint = extractEndpoint(path);

        String payload = shouldUseDowntimeJson(method, endpoint)
                ? generateDowntimeJson(body)
                : body;

        logService.logRequest(login, ip, endpoint, payload);
    }

    private String readRequestBody(ContainerRequestContext requestContext) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(requestContext.getEntityStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RequestBodyReadException("Failed to read request body", e);
        }
    }

    private void resetEntityStream(ContainerRequestContext requestContext, String body) {
        requestContext.setEntityStream(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }

    private boolean shouldSkip(String login, String method) {
        if (login == null || login.isBlank() || DEFAULT_SESSION_ID.equalsIgnoreCase(login)) {
            return true;
        }
        return "GET".equalsIgnoreCase(method);
    }

    private String extractEndpoint(String path) {
        if (path == null || path.isBlank()) {
            return "unknown";
        }
        return path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
    }

    private boolean shouldUseDowntimeJson(String method, String endpoint) {
        if (!"POST".equalsIgnoreCase(method)) {
            return false;
        }
        return "save".equalsIgnoreCase(endpoint) || "stopSolving".equalsIgnoreCase(endpoint);
    }

    private String generateDowntimeJson(String body) {
        try {
            PackagingSchedule solution = objectMapper.readValue(body, PackagingSchedule.class);
            LocalDate dti = solution.getWorkCalendar().getCurrentDate();
            DownTimeData data = calculateDownTime(solution);

            Map<String, Long> lineDownTimesInMinutes = new HashMap<>();
            for (Map.Entry<String, Duration> entry : data.lineDonwTimes().entrySet()) {
                lineDownTimesInMinutes.put(entry.getKey(), entry.getValue().toMinutes());
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("dt", dti.toString());
            payload.put("downtime", data.commonDownTime());
            payload.put("lines", lineDownTimesInMinutes);

            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{\"error\":\"failed to calculate downtime\"}";
        }
    }

    private String getIp(ContainerRequestContext requestContext) {
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
