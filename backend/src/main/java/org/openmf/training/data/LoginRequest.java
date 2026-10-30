package org.openmf.training.data;

/**
 * Login request body — mirrors Fineract authentication payload shape (simplified).
 */
public record LoginRequest(String username, String password) {
}
