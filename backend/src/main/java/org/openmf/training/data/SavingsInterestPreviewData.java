package org.openmf.training.data;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SavingsInterestPreviewData(
        LocalDate fromDate,
        LocalDate toDate,
        int days,
        BigDecimal averageDailyBalance,
        BigDecimal amount,
        String calculationMethod,
        BigDecimal annualInterestRate
) {
}
