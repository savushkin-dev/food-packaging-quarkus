package org.acme.foodpackaging.rest.materials;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.log4j.Log4j2;
import org.acme.foodpackaging.dto.materials.KolfRecalcRequest;
import org.acme.foodpackaging.dto.materials.PpDto;
import org.acme.foodpackaging.dto.materials.ProductWithMaterialsDto;
import org.acme.foodpackaging.dto.materials.SaveRequest;
import org.acme.foodpackaging.service.materials.MaterialService;
import org.acme.foodpackaging.service.materials.config.PpService;

import java.util.List;

@Log4j2
@Path("/api/material")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MaterialResource {

    private final MaterialService materialService;
    private final PpService ppService;

    @Inject
    public MaterialResource(MaterialService materialService, PpService ppService) {
        this.materialService = materialService;
        this.ppService = ppService;
    }

    @GET
    @Path("/recipients/search")
    public Response searchRecipients(@QueryParam("query") String query) {
        if (query == null || query.length() < 2) {
            return Response.ok(List.of()).build();
        }
        try {
            List<PpDto> result = ppService.searchByName(query);
            return Response.ok(result).build();
        } catch (Exception e) {
            String safeQuery = sanitizeForLog(query);
            log.error("Error searching recipients with query: {}", safeQuery, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error searching recipients: " + e.getMessage())
                    .build();
        }
    }

    @GET
    @Path("/load")
    public Response loadProducts(
            @QueryParam("date") String date,
            @QueryParam("kpp") String kpp) {
        try {
            List<ProductWithMaterialsDto> data = materialService.loadProducts(date, kpp);
            return Response.ok(data).build();
        } catch (Exception e) {
            String safeDate = sanitizeForLog(date);
            String safeKpp = sanitizeForLog(kpp);
            log.error("Error loading products for date: {}, kpp: {}", safeDate, safeKpp, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error loading products: " + e.getMessage())
                    .build();
        }
    }

    @POST
    @Path("/recalc")
    public Response recalcKolf(KolfRecalcRequest request) {
        try {
            List<ProductWithMaterialsDto> updated = materialService.recalcKolf(request);
            return Response.ok(updated).build();
        } catch (Exception e) {
            String sanitizedRequest = String.valueOf(request)
                    .replace('\n', '_')
                    .replace('\r', '_');
            log.error("Error recalculating KOLF for request: {}", sanitizedRequest, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error recalculating KOLF: " + e.getMessage())
                    .build();
        }
    }

    @POST
    @Path("/save")
    @Transactional
    public Response saveAll(SaveRequest request) {
        try {
            materialService.saveAll(request);
            return Response.ok().build();
        } catch (Exception e) {
            log.error("Error saving data for request", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error saving data: " + e.getMessage())
                    .build();
        }
    }

    private String sanitizeForLog(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace('\r', '_').replace('\n', '_');
        StringBuilder sanitized = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            sanitized.append(Character.isISOControl(c) ? '_' : c);
        }
        return sanitized.toString();
    }
}