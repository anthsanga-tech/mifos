package org.openmf.training.data;

import java.time.LocalDate;

public record CreateClientRequest(
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
        String gender) {
}
