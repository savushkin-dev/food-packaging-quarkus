package org.acme.foodpackaging.rest;


import io.vertx.core.http.HttpServerRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

@Path("/ip")
public class TestController {


    @GET
    public String getIp(@Context HttpServerRequest request) {
        // сначала пробуем заголовок от прокси
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.remoteAddress().host();
        }
        return ip;
    }



}
