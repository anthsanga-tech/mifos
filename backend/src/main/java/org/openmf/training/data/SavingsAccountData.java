package org.openmf.training.data;

import org.openmf.training.domain.Client;
import org.openmf.training.domain.FreezeStatus;
import org.openmf.training.domain.SavingsAccountStatus;
import org.openmf.training.domain.SavingsProduct;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SavingsAccountData(
        Long id,
        Long clientId,
        Long productId,
        String accountNo,
        SavingsAccountStatus status,
        BigDecimal balance,
        LocalDateTime createdOn,
        FreezeStatus freezeStatus,
        String freezeReason,
        LocalDateTime frozenOn,
        LocalDateTime activatedOn
) {
}