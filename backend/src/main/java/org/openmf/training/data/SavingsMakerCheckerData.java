package org.openmf.training.data;

import org.openmf.training.domain.SavingsMakerCheckerCommandType;
import org.openmf.training.domain.SavingsMakerCheckerStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SavingsMakerCheckerData(
        Long id,
        String savingsAccountNo,
        SavingsMakerCheckerCommandType transactionType,
        String payloadJson,
        BigDecimal amount,
        SavingsMakerCheckerStatus status,
        String madeBy,
        LocalDateTime madeOn,
        String checkedBy,
        LocalDateTime checkedOn,
        String rejectReason
) {
}
