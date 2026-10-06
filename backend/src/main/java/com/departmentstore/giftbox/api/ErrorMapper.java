package com.departmentstore.giftbox.api;

import com.departmentstore.giftbox.api.dto.Dtos.ErrorDto;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** 统一 JSON 错误响应 */
@Provider
public class ErrorMapper implements ExceptionMapper<Exception> {

    @Override
    public Response toResponse(Exception e) {
        int status = 500;
        String message = e.getMessage();
        if (e instanceof WebApplicationException wae) {
            status = wae.getResponse().getStatus();
            if (message == null) {
                message = wae.getResponse().getStatusInfo().getReasonPhrase();
            }
        }
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorDto(message))
                .build();
    }
}
