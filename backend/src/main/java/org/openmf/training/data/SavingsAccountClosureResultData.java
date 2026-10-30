package org.openmf.training.data;

public record SavingsAccountClosureResultData(
        String status,
        String message,
        Long makerCheckerId,
        Long destinationAccountId
) {
}
