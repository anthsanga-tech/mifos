package org.openmf.training.data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SavingsProductData(
        Long id,
        String name,
        BigDecimal nominalAnnualInterestRate,
        BigDecimal minRequiredBalance,
        BigDecimal withdrawalFee,
        BigDecimal closureFee,
        LocalDateTime createdOn
) {
}