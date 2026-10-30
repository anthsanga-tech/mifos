package org.openmf.training.data;

import org.openmf.training.domain.SavingsTransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SavingsTransactionData(
        Long id,
        Long savingsAccountId,
        SavingsTransactionType transactionType,
        BigDecimal amount,
        LocalDateTime transactionDate,
        BigDecimal runningBalance,
        Short reversed,
        String transactionGroupId,
        LocalDate interestPeriodFrom,
        LocalDate interestPeriodTo,
        boolean undoable
) {
}