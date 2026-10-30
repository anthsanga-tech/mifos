package org.openmf.training.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Shared HTTP security defaults for training Basic / OAuth modes.
 */
final class TrainingSecuritySupport {

    private TrainingSecuritySupport() {}

    static void applyCommon(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(org.springframework.security.config.Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/mini-fineract-provider/api/v1/authentication")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/mini-fineract-provider/api/v1/health")
                        .permitAll()
                        .anyRequest()
                        .authenticated());
    }

    /**
     * JSON 401 without WWW-Authenticate: Basic (avoids browser native login popup).
     */
    static AuthenticationEntryPoint jsonUnauthorizedEntryPoint() {
        return (HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) ->
                writeUnauthorized(response);
    }

    static void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"Unauthorized\"}");
    }
}
