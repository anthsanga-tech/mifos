package org.openmf.training.data;

import java.math.BigDecimal;

public record SavingsAccountClosureDestinationData(
        Long accountId,
        String accountNo,
        BigDecimal balance
) {
}
