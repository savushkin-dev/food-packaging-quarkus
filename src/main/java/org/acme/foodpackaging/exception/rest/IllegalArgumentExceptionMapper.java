package org.acme.foodpackaging.exception.rest;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

@Provider
public class IllegalArgumentExceptionMapper extends BaseExceptionMapper<IllegalArgumentException> {

    @Override
    public Response toResponse(IllegalArgumentException e) {
        return buildResponse(e, Response.Status.BAD_REQUEST.getStatusCode());
    }
}

