package org.acme.foodpackaging.config;

import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;


@Provider
@Priority(2)
public class IpSessionFilter implements ContainerRequestFilter {

    @Inject
    HttpServerRequest vertxRequest;

    @Override
    public void filter(ContainerRequestContext requestContext) {
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
            // перезаписываем X-Session-Id
            requestContext.getHeaders().putSingle("X-Session-Id", ip);
        }
    }
}
