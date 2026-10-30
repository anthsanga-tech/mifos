package org.openmf.training.data;

import org.openmf.training.domain.SavingsMakerCheckerCommandType;

import java.math.BigDecimal;

public record CreateSavingsAccountsCheckMakerRequest(
        Long savingsAccountId,
        SavingsMakerCheckerCommandType transactionType,
        BigDecimal amount
) {
}
