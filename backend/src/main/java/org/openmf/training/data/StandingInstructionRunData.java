package org.openmf.training.data;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record StandingInstructionRunData(
        Long id,
        Long standingInstructionId,
        LocalDate dueDate,
        LocalDate businessDate,
        String status,
        String reason,
        String transferGroupId,
        Long makerCheckerRequestId,
        LocalDateTime ranOn
) {
}
