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
import org.openmf.training.data.CreateUserRequest;
import org.openmf.training.data.UpdateUserRequest;
import org.openmf.training.data.UserData;
import org.openmf.training.service.UserWritePlatformService;
import org.springframework.stereotype.Component;

/**
 * Sample users API — same JAX-RS {@code *ApiResource} pattern as Fineract.
 */
@Component
@Path("/v1/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UsersApiResource {

    private final UserWritePlatformService userService;

    public UsersApiResource(UserWritePlatformService userService) {
        this.userService = userService;
    }

    @GET
    public List<UserData> retrieveAll() {
        return userService.retrieveAll();
    }

    @GET
    @Path("{userId}")
    public UserData retrieveOne(@PathParam("userId") Long userId) {
        return userService.retrieveOne(userId);
    }

    @POST
    public UserData create(CreateUserRequest request) {
        return userService.create(request);
    }

    @PUT
    @Path("{userId}")
    public UserData update(@PathParam("userId") Long userId, UpdateUserRequest request) {
        return userService.update(userId, request);
    }

    @POST
    @Path("{userId}")
    public UserData command(
            @PathParam("userId") Long userId,
            @QueryParam("command") String command) {
        return userService.handleCommand(userId, command);
    }
}
