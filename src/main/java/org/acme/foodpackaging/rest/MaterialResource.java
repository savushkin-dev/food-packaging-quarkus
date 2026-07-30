package org.acme.foodpackaging.rest;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acme.foodpackaging.dto.materials.KolfRecalcRequest;
import org.acme.foodpackaging.dto.materials.PpDto;
import org.acme.foodpackaging.dto.materials.ProductWithMaterialsDto;
import org.acme.foodpackaging.dto.materials.SaveRequest;
import org.acme.foodpackaging.service.materials.MaterialService;
import org.acme.foodpackaging.service.materials.PpService;

import java.util.List;

@Path("/api/material")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MaterialResource {

    @Inject
    MaterialService materialService;

    @Inject
    PpService ppService;

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
            e.printStackTrace();
            return Response.status(500).entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("/load")
    public Response loadProducts(
            @QueryParam("date") String date,
            @QueryParam("kpp") String kpp
    ) {
        try {
            List<ProductWithMaterialsDto> data = materialService.loadProducts(date, kpp);
            return Response.ok(data).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(500).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("/recalc")
    public Response recalcKolf(KolfRecalcRequest request) {
        try {
            List<ProductWithMaterialsDto> updated = materialService.recalcKolf(request);
            return Response.ok(updated).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(500).entity(e.getMessage()).build();
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
            e.printStackTrace();
            return Response.status(500).entity(e.getMessage()).build();
        }
    }
}