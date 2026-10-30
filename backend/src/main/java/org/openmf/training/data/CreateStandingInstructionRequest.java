package org.openmf.training.data;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateStandingInstructionRequest(
        Long toAccountId,
        BigDecimal amount,
        String frequency,
        LocalDate nextDue,
        LocalDate validFrom,
        LocalDate validTo
) {
}
