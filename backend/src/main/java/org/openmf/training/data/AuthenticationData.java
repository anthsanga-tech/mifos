package org.openmf.training.data;

/**
 * Successful login response — enough for the UI to show who signed in.
 * (No JWT yet; add later if needed.)
 */
public record AuthenticationData(
        Long userId,
        String username,
        String displayName,
        String email,
        boolean authenticated) {
}
