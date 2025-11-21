package org.acme.foodpackaging.config;

import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

@Provider
@Priority(1) // фильтр будет выполняться до остальных
public class SessionHeaderFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String sessionId = requestContext.getHeaderString("X-Session-Id");
        if (sessionId == null || sessionId.isBlank()) {
            requestContext.getHeaders().add("X-Session-Id", "default_session_id");
        }
    }
}