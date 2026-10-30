package org.openmf.training.config;

import org.openmf.training.api.*;
import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.context.annotation.Configuration;

/**
 * Registers JAX-RS resources — same role as Fineract's Jersey setup with {@code *ApiResource} classes.
 */
@Configuration
public class JerseyConfig extends ResourceConfig {

    public JerseyConfig() {
        register(HealthApiResource.class);
        register(AuthenticationApiResource.class);
        register(UsersApiResource.class);
        register(ClientsApiResource.class);
        register(SavingsProductsApiResource.class);
        register(SavingsAccountsApiResource.class);
        register(SavingsTransactionsApiResource.class);
        register(SavingsAccountsCheckMakerApiResource.class);
        register(ApiExceptionMapper.class);
        register(RequestLoggingFilter.class);
        register(ObjectMapperProvider.class);
    }
}
