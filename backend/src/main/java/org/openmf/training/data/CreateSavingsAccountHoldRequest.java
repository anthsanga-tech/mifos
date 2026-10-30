package org.openmf.training.data;

import java.math.BigDecimal;

public record CreateSavingsAccountHoldRequest(
        BigDecimal amount,
        String reason
) {
}
