package org.acme.foodpackaging.rest;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.LinkedHashMap;
import java.util.Map;

@Provider
public class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(IllegalArgumentException e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", buildOperationErrorLabel());
        body.put("exception", e.getClass().getName());
        body.put("rootCause", root.getClass().getName());
        body.put("message", root.getMessage() == null ? "" : root.getMessage());
        if (uriInfo != null) {
            body.put("path", uriInfo.getPath());
        }

        return Response.status(Response.Status.BAD_REQUEST)
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
        return (last == null || last.isBlank()) ? "Operation error" : last + " error";
    }
}

