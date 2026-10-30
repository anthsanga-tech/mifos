package org.openmf.training.exception;

/**
 * Thrown when username/password do not match an active user.
 */
public class AuthenticationException extends RuntimeException {

    public AuthenticationException(String message) {
        super(message);
    }
}
