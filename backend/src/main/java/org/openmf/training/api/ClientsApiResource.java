package org.openmf.training.api;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.openmf.training.data.ClientData;
import org.openmf.training.data.CreateClientRequest;
import org.openmf.training.data.UpdateClientRequest;
import org.openmf.training.service.ClientWritePlatformService;
import org.springframework.stereotype.Component;

/**
 * Sample clients API — same JAX-RS {@code *ApiResource} pattern as Fineract.
 */
@Component
@Path("/v1/clients")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ClientsApiResource {

    private final ClientWritePlatformService clientService;

    public ClientsApiResource(ClientWritePlatformService clientService) {
        this.clientService = clientService;
    }

    @GET
    public List<ClientData> retrieveAll() {
        return clientService.retrieveAll();
    }

    @GET
    @Path("{clientId}")
    public ClientData retrieveOne(@PathParam("clientId") Long clientId) {
        return clientService.retrieveOne(clientId);
    }

    @POST
    public ClientData create(CreateClientRequest request) {
        return clientService.create(request);
    }

    @PUT
    @Path("{clientId}")
    public ClientData update(@PathParam("clientId") Long clientId, UpdateClientRequest request) {
        return clientService.update(clientId, request);
    }

    @POST
    @Path("{clientId}")
    public ClientData command(
            @PathParam("clientId") Long clientId,
            @QueryParam("command") String command) {
        return clientService.handleCommand(clientId, command);
    }
}
