package org.acme.foodpackaging.rest.materials;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acme.foodpackaging.service.materials.DbfImportService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Path("/api/dbf/import")
@Produces(MediaType.APPLICATION_JSON)
public class DbfImportResource {

    private static final String ERROR_IMPORT_FAILED = "Import failed";

    private final DbfImportService dbfImportService;

    @Inject
    public DbfImportResource(DbfImportService dbfImportService) {
        this.dbfImportService = dbfImportService;
    }

    @POST
    @Path("/sprog")
    public Response importSprog() {
        return runImport(dbfImportService::importSprog);
    }

    @POST
    @Path("/rnpp")
    public Response importRnpp() {
        return runImport(dbfImportService::importRnpp);
    }

    @POST
    @Path("/mt")
    public Response importMt() {
        return runImport(dbfImportService::importMt);
    }

    @POST
    @Path("/pp")
    public Response importPp() {
        return runImport(dbfImportService::importPp);
    }

    private Response runImport(ImportOperation importOperation) {
        try {
            importOperation.run();
            return Response.ok().build();
        } catch (IllegalArgumentException e) {
            log.warn("DBF import request was rejected");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("DBF import is unavailable")
                    .build();
        } catch (Exception e) {
            log.error("DBF import failed");
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ERROR_IMPORT_FAILED)
                    .build();
        }
    }

    @FunctionalInterface
    private interface ImportOperation {
        void run();
    }
}