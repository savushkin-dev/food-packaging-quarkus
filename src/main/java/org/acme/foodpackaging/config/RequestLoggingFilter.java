package org.acme.foodpackaging.config;

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
@Priority(3)
public class RequestLoggingFilter implements ContainerRequestFilter {

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

        logService.logRequest(login, path.substring(path.lastIndexOf('/') + 1), body);
    }

}
