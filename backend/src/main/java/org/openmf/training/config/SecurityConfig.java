package org.openmf.training.config;

/**
 * Replaced by dual-mode configs (Fineract-style):
 * <ul>
 *   <li>{@link BasicSecurityConfig} — {@code training.security.basicauth.enabled=true}</li>
 *   <li>{@link Oauth2SecurityConfig} — {@code training.security.oauth2.enabled=true}</li>
 * </ul>
 * This class is intentionally empty so the old single Basic-only chain is not registered.
 */
public final class SecurityConfig {
    private SecurityConfig() {}
}
