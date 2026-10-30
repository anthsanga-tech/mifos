package org.openmf.training.data;

import java.math.BigDecimal;

public record ClosureSettlementTransferPayload(
        String fromAccountNo,
        String toAccountNo,
        BigDecimal amount,
        BigDecimal closureFee,
        boolean closureSettlement
) {
    public TransferRequest toTransferRequest() {
        return new TransferRequest(fromAccountNo, toAccountNo, amount);
    }
}
