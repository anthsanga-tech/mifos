package org.openmf.training.data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SavingsAccountAuditTrailData(
        Long id,
        Long savingsAccountId,
        LocalDateTime when,
        String event,
        String summary,
        BigDecimal amount,
        String by
) {
}
