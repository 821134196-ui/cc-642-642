package com.giftbox.api;

import com.giftbox.service.ReturnService;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

/** 统一错误响应：业务错误 400，找不到资源 404。 */
@Provider
public class ApiExceptionMappers {

    @Provider
    public static class BusinessMapper implements ExceptionMapper<ReturnService.BusinessException> {
        @Override
        public Response toResponse(ReturnService.BusinessException e) {
            return Response.status(400)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @Provider
    public static class NotFoundMapper implements ExceptionMapper<ReturnService.NotFoundException> {
        @Override
        public Response toResponse(ReturnService.NotFoundException e) {
            return Response.status(404)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @Provider
    public static class IllegalArgMapper implements ExceptionMapper<IllegalArgumentException> {
        @Override
        public Response toResponse(IllegalArgumentException e) {
            return Response.status(400)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }
}
