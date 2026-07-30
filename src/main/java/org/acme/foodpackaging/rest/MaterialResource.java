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
import org.acme.foodpackaging.entity.materials.PlrPp;
import org.acme.foodpackaging.service.materials.MaterialService;

import java.util.List;

@Path("/api/material")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MaterialResource {

    @Inject
    MaterialService materialService;

    // 1. Получить список получателей
    @GET
    @Path("/recipients")
    public Response getRecipients() {
        List<PlrPp> recipients = materialService.getRecipients();
        return Response.ok(recipients).build();
    }

    @GET
    @Path("/recipients/search")
    public Response searchRecipients(@QueryParam("query") String query) {
        try {
            List<PpDto> result = materialService.searchRecipients(query);
            return Response.ok(result).build();
        } catch (Exception e) {
            return Response.status(500).entity(e.getMessage()).build();
        }
    }

    // 2. Загрузка продуктов и материалов (БЕЗ СОХРАНЕНИЯ)
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

    // 3. Пересчет KOLF (БЕЗ СОХРАНЕНИЯ)
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

    // 4. Сохранение всех данных
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