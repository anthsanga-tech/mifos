package org.openmf.training.data;

import org.openmf.training.domain.SavingsTransactionType;

import java.math.BigDecimal;

public record CreateSavingsTransactionRequest(
        Long savingsAccountId,
        SavingsTransactionType transactionType,
        BigDecimal amount
) {
}