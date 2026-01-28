package org.acme.foodpackaging.rest;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

@Provider
public class GlobalExceptionHandler extends BaseExceptionMapper<IllegalStateException> {
    @Override
    public Response toResponse(IllegalStateException e) {
        return buildResponse(e, Response.Status.BAD_REQUEST.getStatusCode());
    }
}
