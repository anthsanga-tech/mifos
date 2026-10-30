package org.openmf.training.data;

import java.math.BigDecimal;

public record CreateSavingsProductRequest(
        String name,
        BigDecimal nominalAnnualInterestRate,
        BigDecimal minRequiredBalance,
        BigDecimal withdrawalFee,
        BigDecimal closureFee
) {
}