package org.openmf.training.data;

import java.math.BigDecimal;
import java.util.List;

public record SavingsAccountClosurePreviewData(
        boolean canClose,
        BigDecimal balance,
        BigDecimal availableBalance,
        BigDecimal closureFee,
        BigDecimal residualAmount,
        BigDecimal activeHoldAmount,
        long activeHoldCount,
        long openStandingInstructionCount,
        long pendingMakerCheckerCount,
        boolean frozen,
        boolean requiresApproval,
        List<SavingsAccountClosureDestinationData> eligibleDestinations,
        List<String> blockers
) {
}
