package org.acme.foodpackaging.rest;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.LinkedHashMap;
import java.util.Map;

@Provider
@Priority(Priorities.USER)
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(Throwable e) {
        Throwable root = rootCause(e);

        int status = Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
        if (e instanceof WebApplicationException wae && wae.getResponse() != null) {
            status = wae.getResponse().getStatus();
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", buildOperationErrorLabel());
        body.put("exception", e.getClass().getName());
        body.put("rootCause", root.getClass().getName());
        body.put("message", root.getMessage() == null ? "" : root.getMessage());
        if (uriInfo != null) {
            body.put("path", uriInfo.getPath());
        }

        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }

    private String buildOperationErrorLabel() {
        if (uriInfo == null || uriInfo.getPath() == null || uriInfo.getPath().isBlank()) {
            return "Operation error";
        }
        String path = uriInfo.getPath();
        String[] parts = path.split("/");
        String last = parts.length == 0 ? path : parts[parts.length - 1];
        if (last.isBlank()) {
            return "Operation error";
        }
        return last + " error";
    }

    private static Throwable rootCause(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }
}

