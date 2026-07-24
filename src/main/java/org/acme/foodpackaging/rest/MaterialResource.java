package org.acme.foodpackaging.rest;


import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acme.foodpackaging.dto.materials.LoadMaterialReqDto;
import org.acme.foodpackaging.dto.materials.ProductDto;
import org.acme.foodpackaging.dto.materials.ProductWithMaterialsDto;
import org.acme.foodpackaging.entity.materials.Pp;
import org.acme.foodpackaging.service.materials.MaterialService;

import java.util.List;


@Path("/api/material")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MaterialResource {

    private final MaterialService materialService;

    @Inject
    public MaterialResource(MaterialService materialService) {
        this.materialService = materialService;
    }


    @GET
    @Path("/products")
    public Response getProducts(@QueryParam("date") String date) {
        try {
            List<ProductDto> products = materialService.getProductsByDate(date);
            return Response.ok(products).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(500).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("/load")
    public Response loadProducts(LoadMaterialReqDto loadMaterialReqDto) {
        try {
            List<ProductWithMaterialsDto> result = materialService.loadProductsToZinv(loadMaterialReqDto.getDate(), loadMaterialReqDto.getKpp());
            return Response.ok(result).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(500).entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("/recipients")
    public Response getRecipients() {
        try {
            List<Pp> products = materialService.getRecipients();
            return Response.ok(products).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(500).entity(e.getMessage()).build();
        }
    }


}
