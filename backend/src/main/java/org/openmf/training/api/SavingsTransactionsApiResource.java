package org.openmf.training.api;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

import org.openmf.training.data.*;
import org.openmf.training.service.SavingsTransactionWritePlatformService;
import org.springframework.stereotype.Component;

@Component
@Path("/v1/savings-transactions")
@Produces(MediaType.APPLICATION_JSON)
public class SavingsTransactionsApiResource {

    private final SavingsTransactionWritePlatformService transactionService;

    public SavingsTransactionsApiResource(
            SavingsTransactionWritePlatformService transactionService
    ) {
        this.transactionService = transactionService;
    }

    @GET
    public List<SavingsTransactionData> retrieveByAccount(
            @QueryParam("accountId") Long accountId
    ) {
        return transactionService.retrieveByAccount(accountId);
    }

    @POST
    public Object create(
            CreateSavingsTransactionRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Transaction request is required");
        }
        return transactionService.create(request);
    }

    @POST
    @Path("{accountId}/interest-preview")
    public SavingsInterestPreviewData previewInterest(
            @PathParam("accountId") Long accountId,
            SavingsInterestRequest request
    ) {
        return transactionService.previewInterest(accountId, request);
    }

    @POST
    @Path("{accountId}/interest-posting")
    public Object postInterest(
            @PathParam("accountId") Long accountId,
            SavingsInterestRequest request
    ) {
        return transactionService.postInterest(accountId, request);
    }

    @POST
    @Path("{accountId}/transactions/{transactionId}")
    public SavingsTransactionData undoTransaction(@PathParam("accountId") Long accountId, @PathParam("transactionId") Long transactionId) {
        return transactionService.undoTransaction(accountId, transactionId);
    }

    @POST
    @Path("/transfer")
    public Object transfer(TransferRequest transferRequest) {
        return transactionService.transfer(transferRequest);
    }
}