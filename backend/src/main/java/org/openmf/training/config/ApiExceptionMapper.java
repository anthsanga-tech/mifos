package org.openmf.training.config;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;
import org.openmf.training.exception.AuthenticationException;
import org.openmf.training.exception.PlatformApiDataValidationException;
import org.openmf.training.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Provider
@Component
public class ApiExceptionMapper implements ExceptionMapper<RuntimeException> {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionMapper.class);

    @Override
    public Response toResponse(RuntimeException exception) {
        if (exception instanceof ResourceNotFoundException) {
            log.warn("404 {}", exception.getMessage());
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("message", exception.getMessage()))
                    .build();
        }
        if (exception instanceof AuthenticationException) {
            log.warn("401 {}", exception.getMessage());
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("message", exception.getMessage()))
                    .build();
        }
        if (exception instanceof PlatformApiDataValidationException || exception instanceof IllegalArgumentException
                || exception instanceof IllegalStateException) {
            log.warn("400 {}", exception.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("message", exception.getMessage()))
                    .build();
        }
        log.error("500 unexpected error", exception);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("message", "Unexpected error: " + exception.getMessage()))
                .build();
    }
}
