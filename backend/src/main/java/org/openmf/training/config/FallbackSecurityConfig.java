package org.openmf.training.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Fallback when neither Basic nor OAuth flags are set — deny all except health.
 * Prefer enabling exactly one of {@code training.security.basicauth.enabled} or
 * {@code training.security.oauth2.enabled}.
 */
@Configuration
@EnableWebSecurity
@ConditionalOnMissingBean(SecurityFilterChain.class)
public class FallbackSecurityConfig {

    @Bean
    public SecurityFilterChain fallbackSecurityFilterChain(HttpSecurity http) throws Exception {
        TrainingSecuritySupport.applyCommon(http);
        http.httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
