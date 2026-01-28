package org.acme.foodpackaging.rest;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

@Provider
@Priority(Priorities.USER)
public class GlobalExceptionMapper extends BaseExceptionMapper<Throwable> {

    @Override
    public Response toResponse(Throwable e) {
        int status = Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
        if (e instanceof WebApplicationException wae && wae.getResponse() != null) {
            status = wae.getResponse().getStatus();
        }
        return buildResponse(e, status);
    }
}

