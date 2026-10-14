package org.acme.foodpackaging.rest.materials;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.log4j.Log4j2;
import org.acme.foodpackaging.dto.materials.ApiError;
import org.acme.foodpackaging.service.materials.DbfImportService;

@Log4j2
@Path("/api/dbf/import")
@Produces(MediaType.APPLICATION_JSON)
public class DbfImportResource {

    private static final String ERROR_IMPORT_FAILED = "Не удалось выполнить импорт DBF";
    private static final String ERROR_IMPORT_UNAVAILABLE = "DBF import is unavailable";

    private final DbfImportService dbfImportService;

    @Inject
    public DbfImportResource(DbfImportService dbfImportService) {
        this.dbfImportService = dbfImportService;
    }

    @POST
    @Path("/sprog")
    public Response importSprog() {
        return runImport("SPROG", dbfImportService::importSprog);
    }

    @POST
    @Path("/rnpp")
    public Response importRnpp() {
        return runImport("RNPP", dbfImportService::importRnpp);
    }

    @POST
    @Path("/mt")
    public Response importMt() {
        return runImport("MT", dbfImportService::importMt);
    }

    @POST
    @Path("/pp")
    public Response importPp() {
        return runImport("PP", dbfImportService::importPp);
    }

    private Response runImport(String tag, ImportOperation importOperation) {
        try {
            importOperation.run();
            return Response.ok().build();
        } catch (IllegalArgumentException e) {
            return warn("[" + tag + "] " + ERROR_IMPORT_UNAVAILABLE, e);
        } catch (Exception e) {
            return error("[" + tag + "] " + ERROR_IMPORT_FAILED, e);
        }
    }

    /**
     * Ошибка клиента: файл не найден, путь недоступен и т.п.
     */
    private Response warn(String message, Exception e) {
        log.warn("{}: {}", message, e.getMessage());
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(message + ": " + e.getMessage()))
                .build();
    }

    /**
     * Внутренняя ошибка: упал импорт целиком.
     */
    private Response error(String message, Exception e) {
        log.error(message, e);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ApiError(message + ": " + e.getMessage()))
                .build();
    }

    @FunctionalInterface
    private interface ImportOperation {
        void run();
    }
}