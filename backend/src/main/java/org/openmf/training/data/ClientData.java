package org.openmf.training.data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.openmf.training.domain.ClientStatus;

public record ClientData(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String address,
        String city,
        String state,
        String zipCode,
        String country,
        LocalDate dateOfBirth,
        String gender,
        ClientStatus status,
        LocalDateTime createdOn) {
}
