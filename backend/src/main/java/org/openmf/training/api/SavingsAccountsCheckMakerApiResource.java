package org.openmf.training.api;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.openmf.training.data.CreateSavingsAccountsCheckMakerRequest;
import org.openmf.training.data.RejectSavingsMakerCheckerRequest;
import org.openmf.training.data.SavingsMakerCheckerData;
import org.openmf.training.domain.SavingsMakerCheckerStatus;
import org.openmf.training.service.SavingsAccountsCheckMakerWritePlatformService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Path("/v1/savings-accounts-check-maker")
@Produces(MediaType.APPLICATION_JSON)
public class SavingsAccountsCheckMakerApiResource {

    private final SavingsAccountsCheckMakerWritePlatformService checkMakerService;

    public SavingsAccountsCheckMakerApiResource(
            SavingsAccountsCheckMakerWritePlatformService checkMakerService
    ) {
        this.checkMakerService = checkMakerService;
    }

    @GET
    public List<SavingsMakerCheckerData> retrieve(
            @QueryParam("status") SavingsMakerCheckerStatus status,
            @QueryParam("accountId") Long accountId
    ) {
        return checkMakerService.retrieve(status, accountId);
    }

    @POST
    public SavingsMakerCheckerData create(CreateSavingsAccountsCheckMakerRequest request) {
        return checkMakerService.create(request);
    }

    @POST
    @Path("{requestId}/approve")
    public SavingsMakerCheckerData approve(@PathParam("requestId") Long requestId) {
        return checkMakerService.approve(requestId);
    }

    @POST
    @Path("{requestId}/reject")
    public SavingsMakerCheckerData reject(
            @PathParam("requestId") Long requestId,
            RejectSavingsMakerCheckerRequest request
    ) {
        return checkMakerService.reject(requestId, request == null ? null : request.reason());
    }

    @POST
    @Path("{requestId}/cancel")
    public SavingsMakerCheckerData cancel(@PathParam("requestId") Long requestId) {
        return checkMakerService.cancel(requestId);
    }
}
