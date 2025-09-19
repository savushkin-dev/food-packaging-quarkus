package org.acme.foodpackaging.rest;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
public class GlobalExceptionHandler implements ExceptionMapper<IllegalStateException> {
    @Override
    public Response toResponse(IllegalStateException e) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", e.getMessage()))
                .build();
    }
}
