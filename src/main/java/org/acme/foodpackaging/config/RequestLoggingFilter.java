package org.acme.foodpackaging.config;

import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.acme.foodpackaging.service.LogService;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Provider
@Priority(2)
public class RequestLoggingFilter implements ContainerRequestFilter {

    @Inject
    HttpServerRequest vertxRequest;

    @Inject
    LogService logService;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();
        String login = requestContext.getHeaderString("X-Session-Id");

        InputStream entityStream = requestContext.getEntityStream();
        String body = new BufferedReader(new InputStreamReader(entityStream, StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n"));

        requestContext.setEntityStream(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));

        String ip = getIp(requestContext);

        logService.logRequest(login, ip, path.substring(path.lastIndexOf('/') + 1), body);
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
