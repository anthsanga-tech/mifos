package org.openmf.training.api;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

import org.openmf.training.data.CreateSavingsProductRequest;
import org.openmf.training.data.SavingsProductData;
import org.openmf.training.service.SavingsProductWritePlatformService;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;

@Component
@Path("/v1/savings-products")
@Produces(MediaType.APPLICATION_JSON)
public class SavingsProductsApiResource {

    private final SavingsProductWritePlatformService productService;

    public SavingsProductsApiResource(
            SavingsProductWritePlatformService productService
    ) {
        this.productService = productService;
    }

    @GET
    public List<SavingsProductData> retrieveAll() {
        return productService.retrieveAll();
    }

    @GET
    @Path("{productId}")
    public SavingsProductData retrieveOne(
            @PathParam("productId") Long productId
    ) {
        return productService.retrieveOne(productId);
    }

    @POST
    public SavingsProductData create(
            CreateSavingsProductRequest request
    ) {
        return productService.create(request);
    }

    @PUT
    @Path("{productId}")
    public SavingsProductData update(
            @PathParam("productId") Long productId,
            CreateSavingsProductRequest request
    ) {
        return productService.update(productId, request);
    }

}