package org.openmf.training.service;

import java.util.List;
import org.openmf.training.data.CreateUserRequest;
import org.openmf.training.data.UpdateUserRequest;
import org.openmf.training.data.UserData;
import org.openmf.training.domain.AppUser;
import org.openmf.training.domain.UserStatus;
import org.openmf.training.exception.PlatformApiDataValidationException;
import org.openmf.training.exception.ResourceNotFoundException;
import org.openmf.training.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sample write/read service for users — same layering idea as Fineract platform services.
 */
@Service
public class UserWritePlatformService {

    private static final Logger log = LoggerFactory.getLogger(UserWritePlatformService.class);

    private final AppUserRepository userRepository;

    public UserWritePlatformService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<UserData> retrieveAll() {
        List<UserData> users = userRepository.findAll().stream().map(this::toData).toList();
        log.debug("retrieveAll count={}", users.size());
        return users;
    }

    @Transactional(readOnly = true)
    public UserData retrieveOne(Long id) {
        log.debug("retrieveOne id={}", id);
        return toData(findUser(id));
    }

    @Transactional
    public UserData create(CreateUserRequest request) {
        if (request == null || isBlank(request.username()) || isBlank(request.displayName())) {
            throw new PlatformApiDataValidationException("username and displayName are required");
        }
        String username = request.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw new PlatformApiDataValidationException("username already exists: " + username);
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setDisplayName(request.displayName().trim());
        user.setEmail(request.email() == null ? null : request.email().trim());
        user.setPassword("password");
        user.setStatus(UserStatus.ACTIVE);
        UserData saved = toData(userRepository.save(user));
        log.info("Created user id={} username={}", saved.id(), saved.username());
        return saved;
    }

    /**
     * Update display name / email — username stays immutable after create.
     */
    @Transactional
    public UserData update(Long id, UpdateUserRequest request) {
        if (request == null || isBlank(request.displayName())) {
            throw new PlatformApiDataValidationException("displayName is required");
        }
        AppUser user = findUser(id);
        user.setDisplayName(request.displayName().trim());
        user.setEmail(request.email() == null ? null : request.email().trim());
        UserData saved = toData(userRepository.save(user));
        log.info("Updated user id={} displayName={}", saved.id(), saved.displayName());
        return saved;
    }

    /**
     * Command-style sample (like Fineract {@code ?command=}).
     */
    @Transactional
    public UserData handleCommand(Long id, String command) {
        if (command == null || command.isBlank()) {
            throw new PlatformApiDataValidationException("Query param 'command' is required");
        }
        AppUser user = findUser(id);
        return switch (command.trim().toLowerCase()) {
            case "disable" -> {
                user.setStatus(UserStatus.DISABLED);
                UserData saved = toData(userRepository.save(user));
                log.info("Disabled user id={}", id);
                yield saved;
            }
            case "activate" -> {
                user.setStatus(UserStatus.ACTIVE);
                UserData saved = toData(userRepository.save(user));
                log.info("Activated user id={}", id);
                yield saved;
            }
            default -> throw new PlatformApiDataValidationException(
                    "Unknown command: " + command + ". Use disable|activate");
        };
    }

    private AppUser findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private UserData toData(AppUser user) {
        return new UserData(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getStatus(),
                user.getCreatedOn());
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
