package org.openmf.training.data;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StandingInstructionData(
        Long id,
        Long fromAccountId,
        Long toAccountId,
        String fromAccountNo,
        String toAccountNo,
        BigDecimal amount,
        String frequency,
        LocalDate nextDue,
        String status,
        LocalDate validFrom,
        LocalDate validTo
) {
}
