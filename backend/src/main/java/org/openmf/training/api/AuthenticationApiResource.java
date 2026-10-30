package org.openmf.training.api;

import jakarta.annotation.PreDestroy;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.openmf.training.data.AuthenticationData;
import org.openmf.training.data.LoginRequest;
import org.openmf.training.service.AuthenticationWritePlatformService;
import org.springframework.stereotype.Component;

/**
 * Authentication API — similar idea to Fineract {@code /authentication} (simplified).
 * POST body: {@code { "username": "mifos", "password": "password" }}
 */
@Component
@Path("/v1/authentication")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthenticationApiResource {

    private final AuthenticationWritePlatformService authenticationService;

    public AuthenticationApiResource(AuthenticationWritePlatformService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @POST
    public AuthenticationData authenticate(LoginRequest request) {
        return authenticationService.authenticate(request);
    }

    @PreDestroy
    public void destroy() {
    }
}
