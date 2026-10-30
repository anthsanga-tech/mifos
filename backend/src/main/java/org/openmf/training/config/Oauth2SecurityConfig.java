package org.openmf.training.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Fineract-style OAuth2 Resource Server (JWT from Keycloak) —
 * enabled when {@code training.security.oauth2.enabled=true}.
 */
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(name = "training.security.oauth2.enabled", havingValue = "true")
public class Oauth2SecurityConfig {

    @Bean
    public SecurityFilterChain oauth2SecurityFilterChain(HttpSecurity http) throws Exception {
        TrainingSecuritySupport.applyCommon(http);
        http.oauth2ResourceServer(oauth2 -> oauth2
                .jwt(Customizer.withDefaults())
                .authenticationEntryPoint((request, response, authException) ->
                        TrainingSecuritySupport.writeUnauthorized(response)));
        return http.build();
    }
}
