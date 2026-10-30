package org.openmf.training.data;

import java.time.LocalDateTime;
import org.openmf.training.domain.UserStatus;

public record UserData(
        Long id,
        String username,
        String displayName,
        String email,
        UserStatus status,
        LocalDateTime createdOn) {
}
