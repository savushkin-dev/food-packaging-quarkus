package org.acme.foodpackaging.exception.rest;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.core.Context;

import java.util.LinkedHashMap;
import java.util.Map;

import org.acme.foodpackaging.rest.ApiFields;

/**
 * Base mapper with shared error body construction to reduce duplication.
 */
abstract class BaseExceptionMapper<T extends Throwable> implements ExceptionMapper<T> {

    @Context
    UriInfo uriInfo;

    protected Response buildResponse(T e, int status) {
        Throwable root = rootCause(e);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put(ApiFields.ERROR, buildOperationErrorLabel());
        body.put(ApiFields.EXCEPTION, e.getClass().getName());
        body.put(ApiFields.ROOT_CAUSE, root.getClass().getName());
        body.put(ApiFields.MESSAGE, root.getMessage() == null ? "" : root.getMessage());
        if (uriInfo != null) {
            body.put(ApiFields.PATH, uriInfo.getPath());
        }
e.printStackTrace();
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }

    protected String buildOperationErrorLabel() {
        if (uriInfo == null || uriInfo.getPath() == null || uriInfo.getPath().isBlank()) {
            return "Operation error";
        }
        String path = uriInfo.getPath(); // e.g. "schedule/moveJobs"
        String[] parts = path.split("/");
        String last = parts.length == 0 ? path : parts[parts.length - 1];
        return (last == null || last.isBlank()) ? "Operation error" : last + " error";
    }

    protected static Throwable rootCause(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }
}

