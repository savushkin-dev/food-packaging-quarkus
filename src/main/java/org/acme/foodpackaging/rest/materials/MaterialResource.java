package org.acme.foodpackaging.rest.materials;

import io.vertx.ext.web.RoutingContext;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.log4j.Log4j2;
import org.acme.foodpackaging.dto.materials.*;
import org.acme.foodpackaging.service.materials.MaterialService;
import org.acme.foodpackaging.service.materials.OneCLogService;
import org.acme.foodpackaging.service.materials.config.PpService;

import java.util.List;

@Log4j2
@Path("/api/material")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MaterialResource {

    private final MaterialService materialService;
    private final PpService ppService;
    private final OneCLogService oneCLogService;

    @Inject
    public MaterialResource(MaterialService materialService, PpService ppService, OneCLogService oneCLogService) {
        this.materialService = materialService;
        this.ppService = ppService;
        this.oneCLogService = oneCLogService;
    }

    @GET
    @Path("/log-1c")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getOneCLog(
            @QueryParam("date") String date,
            @QueryParam("kpp") String kpp,
            @QueryParam("type") String type) {
        try {
            List<OneCReqGroupDto> result = oneCLogService.getLog(date, kpp, type);
            return Response.ok(result).build();
        } catch (Exception e) {
            return error("Не удалось получить лог 1С", e);
        }
    }

    @POST
    @Path("/send-1c")
    public Response sendTo1C(SaveRequest request, @Context RoutingContext ctx) {
        try {
            String ip = ctx.request().remoteAddress().host();

            List<ProductWithMaterialsDto> data = materialService.sendTo1C(request, ip);
            return Response.ok(data).build();
        } catch (Exception e) {
            return error("Не удалось отправить заявку в 1С", e);
        }
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
            return error("Не удалось найти получателей", e);
        }
    }

    @GET
    @Path("/load")
    public Response loadProducts(
            @QueryParam("date") String date,
            @QueryParam("kpp") String kpp,
            @QueryParam("type") String type) {
        try {
            List<ProductWithMaterialsDto> data = materialService.loadProducts(date, kpp, type);
            return Response.ok(data).build();
        } catch (Exception e) {
            return error("Не удалось загрузить продукты", e);
        }
    }

    @GET
    @Path("/reset")
    public Response reset(
            @QueryParam("date") String date,
            @QueryParam("kpp") String kpp,
            @QueryParam("type") String type) {
        try {
            List<ProductWithMaterialsDto> result = materialService.resetDataAndLoadProduct(date, kpp, type);
            return Response.ok(result).build();
        } catch (Exception e) {
            return error("Не удалось сбросить и загрузить продукты", e);
        }
    }

    @POST
    @Path("/recalc")
    public Response recalcKolf(KolfRecalcRequest request) {
        try {
            List<ProductWithMaterialsDto> updated = materialService.recalcKolf(request);
            return Response.ok(updated).build();
        } catch (Exception e) {
            return error("Не удалось пересчитать KOLF", e);
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
            return error("Не удалось сохранить данные", e);
        }
    }

    /**
     * Получить настройки материалов для даты и МОЛ
     */
    @GET
    @Path("/settings")
    public Response getSettings(@QueryParam("date") String date) {
        try {
            List<MaterialSettingDto> settings = materialService.getMaterialsSettings(date);
            return Response.ok(settings).build();
        } catch (Exception e) {
            return error("Не удалось получить настройки материалов", e);
        }
    }

    /**
     * Сохранить настройки материалов
     */
    @PUT
    @Path("/settings")
    public Response saveSettings(List<MaterialSettingDto> settings) {
        try {
            materialService.saveMaterialsSettings(settings);
            return Response.ok().build();
        } catch (Exception e) {
            return error("Не удалось сохранить настройки материалов", e);
        }
    }

    /**
     * Единый ответ на ошибку: короткое сообщение + таймстемп.
     * Полный стек и исходное сообщение исключения остаются только в логе.
     */
    private Response error(String message, Exception e) {
        log.error(message, e);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ApiError(message + ": " + e.getMessage()))
                .build();
    }
}