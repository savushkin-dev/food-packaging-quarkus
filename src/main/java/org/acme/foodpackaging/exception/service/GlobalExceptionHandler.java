package org.acme.foodpackaging.exception.service;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
public class GlobalExceptionHandler implements ExceptionMapper<PackagingException> {

    @Override
    public Response toResponse(PackagingException ex) {
        return Response.status(ex.getStatus())
                .entity(Map.of(
                        "error", ex.getMessage(),
                        // Фронтенд везде читает текст ошибки из e.response.data.message
                        // (см. ScheduleService/SchedulePage.jsx) - без этого поля он
                        // получал бы "Ошибка ...: undefined" для любого PackagingException,
                        // хотя структурированное тело ответа уже пришло корректно.
                        "message", ex.getMessage(),
                        "type", ex.getClass().getSimpleName()
                ))
                .build();
    }
}
