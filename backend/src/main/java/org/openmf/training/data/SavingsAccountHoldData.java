package org.openmf.training.data;

import org.openmf.training.domain.SavingsAccountHoldStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SavingsAccountHoldData(
         Long id,
         Long accountId,
         BigDecimal amount,
         String reason,
         SavingsAccountHoldStatus status,
         LocalDateTime createdOn,
         LocalDateTime releasedOn
) {
}
