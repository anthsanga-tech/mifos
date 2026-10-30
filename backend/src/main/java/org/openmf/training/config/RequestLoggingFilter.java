package org.openmf.training.config;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Logs every JAX-RS request — helpful for juniors watching the Console / terminal.
 */
@Provider
@Component
public class RequestLoggingFilter implements ContainerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        log.info(
                "API {} {}",
                requestContext.getMethod(),
                requestContext.getUriInfo().getRequestUri());
    }
}
