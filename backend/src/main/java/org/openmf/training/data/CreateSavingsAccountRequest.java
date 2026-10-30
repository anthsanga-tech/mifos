package org.openmf.training.data;

import org.openmf.training.domain.SavingsAccountStatus;

public record CreateSavingsAccountRequest(
        Long clientId,
        Long productId,
        String accountNo
) {
}