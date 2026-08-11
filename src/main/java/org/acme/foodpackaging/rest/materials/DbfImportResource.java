package org.acme.foodpackaging.rest.materials;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acme.foodpackaging.dto.materials.FileUploadDto;
import org.acme.foodpackaging.service.materials.DbfImportService;
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Path("/api/dbf/import")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DbfImportResource {

    private final DbfImportService dbfImportService;

    @Inject
    public DbfImportResource(DbfImportService dbfImportService) {
        this.dbfImportService = dbfImportService;
    }

    @POST
    @Path("/sprog")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response importSprog(@MultipartForm FileUploadDto form) {
        return importFile(form, "sprog", dbfImportService::importSprog);
    }

    @POST
    @Path("/sprog/path")
    public Response importSprogByPath(@QueryParam("path") String path) {
        return importByPath(path, dbfImportService::importSprog);
    }

    @POST
    @Path("/rnpp")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response importRnpp(@MultipartForm FileUploadDto form) {
        return importFile(form, "rnpp", dbfImportService::importRnpp);
    }

    @POST
    @Path("/rnpp/path")
    public Response importRnppByPath(@QueryParam("path") String path) {
        return importByPath(path, dbfImportService::importRnpp);
    }

    @POST
    @Path("/mt")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response importMT(@MultipartForm FileUploadDto form) {
        return importFile(form, "mt", dbfImportService::importMt);
    }

    @POST
    @Path("/mt/path")
    public Response importMTByPath(@QueryParam("path") String path) {
        return importByPath(path, dbfImportService::importMt);
    }

    @POST
    @Path("/pp")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response importPP(@MultipartForm FileUploadDto form) {
        return importFile(form, "pp", dbfImportService::importPp);
    }

    @POST
    @Path("/pp/path")
    public Response importPPByPath(@QueryParam("path") String path) {
        return importByPath(path, dbfImportService::importPp);
    }

    // ===== ОБЩИЕ МЕТОДЫ =====

    private Response importFile(FileUploadDto form, String prefix, ImportFunction importer) {
        if (form.file == null) {
            return Response.status(400).entity("File is required").build();
        }

        java.nio.file.Path tempFile = null;
        try {
            tempFile = Files.createTempFile(prefix + "_", ".dbf");
            Files.copy(form.file, tempFile, StandardCopyOption.REPLACE_EXISTING);
            importer.importFile(tempFile.toString());
            return Response.ok().build();
        } catch (Exception e) {
            log.error("Error importing {}", prefix, e);
            return Response.status(500).entity("Import failed: " + e.getMessage()).build();
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception ignored) {}
            }
        }
    }

    private Response importByPath(String path, ImportFunction importer) {
        if (path == null || path.trim().isEmpty()) {
            return Response.status(400).entity("Path is required").build();
        }
        try {
            importer.importFile(path);
            return Response.ok().build();
        } catch (Exception e) {
            log.error("Error importing from path: {}", path, e);
            return Response.status(500).entity("Import failed: " + e.getMessage()).build();
        }
    }

    @FunctionalInterface
    private interface ImportFunction {
        void importFile(String path) throws Exception;
    }
}