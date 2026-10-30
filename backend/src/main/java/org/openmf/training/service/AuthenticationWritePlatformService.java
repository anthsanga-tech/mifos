package org.openmf.training.service;

import org.openmf.training.data.AuthenticationData;
import org.openmf.training.data.LoginRequest;
import org.openmf.training.domain.AppUser;
import org.openmf.training.domain.UserStatus;
import org.openmf.training.exception.AuthenticationException;
import org.openmf.training.exception.PlatformApiDataValidationException;
import org.openmf.training.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication service — training counterpart to Fineract login checks (simplified).
 */
@Service
public class AuthenticationWritePlatformService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationWritePlatformService.class);

    private final AppUserRepository userRepository;

    public AuthenticationWritePlatformService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public AuthenticationData authenticate(LoginRequest request) {
        if (request == null || isBlank(request.username()) || isBlank(request.password())) {
            throw new PlatformApiDataValidationException("username and password are required");
        }

        String username = request.username().trim();
        log.debug("Login attempt for username={}", username);

        AppUser user = userRepository
                .findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("Login failed — user not found: {}", username);
                    return new AuthenticationException("Invalid username or password");
                });

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Login failed — user disabled: {}", username);
            throw new AuthenticationException("User is disabled");
        }

        if (!user.getPassword().equals(request.password())) {
            log.warn("Login failed — bad password for username={}", username);
            throw new AuthenticationException("Invalid username or password");
        }

        log.info("Login OK userId={} username={}", user.getId(), user.getUsername());
        return new AuthenticationData(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                true);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
