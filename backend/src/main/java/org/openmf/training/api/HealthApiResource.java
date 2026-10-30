package org.openmf.training.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
@Path("/v1/health")
@Produces(MediaType.APPLICATION_JSON)
public class HealthApiResource {

    @GET
    public Map<String, String> health() {
        return Map.of("status", "UP", "app", "mini-fineract-training");
    }
}
