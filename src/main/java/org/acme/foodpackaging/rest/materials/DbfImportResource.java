package org.acme.foodpackaging.rest.materials;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acme.foodpackaging.service.materials.DbfImportService;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;

@Path("/api/dbf/import")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DbfImportResource {

    @Inject
    DbfImportService dbfImportService;

    @POST
    @Path("/sprog")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response importSprog(@FormParam("file") InputStream fileStream,
                                @FormParam("file") String fileName) {
        try {
            File tempFile = File.createTempFile("sprog_", ".dbf");
            tempFile.deleteOnExit();
            Files.copy(fileStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            int count = dbfImportService.importSprog(tempFile.getAbsolutePath());
            tempFile.delete();

            return Response.ok(Map.of(
                    "success", true,
                    "table", "PLR_SPROG",
                    "imported", count
            )).build();

        } catch (Exception e) {
            return Response.serverError()
                    .entity(Map.of("success", false, "error", e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/rnpp")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response importRnpp(@FormParam("file") InputStream fileStream,
                               @FormParam("file") String fileName) {
        try {
            File tempFile = File.createTempFile("rnpp_", ".dbf");
            tempFile.deleteOnExit();
            Files.copy(fileStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            int count = dbfImportService.importRnpp(tempFile.getAbsolutePath());
            tempFile.delete();

            return Response.ok(Map.of(
                    "success", true,
                    "table", "PLR_RNPP",
                    "imported", count
            )).build();

        } catch (Exception e) {
            return Response.serverError()
                    .entity(Map.of("success", false, "error", e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/sprog/path")
    public Response importSprogByPath(@QueryParam("path") String path) {
        try {
            int count = dbfImportService.importSprog(path);
            return Response.ok(Map.of(
                    "success", true,
                    "imported", count
            )).build();
        } catch (Exception e) {
            return Response.serverError()
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/rnpp/path")
    public Response importRnppByPath(@QueryParam("path") String path) {
        try {
            int count = dbfImportService.importRnpp(path);
            return Response.ok(Map.of(
                    "success", true,
                    "imported", count
            )).build();
        } catch (Exception e) {
            return Response.serverError()
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }
}