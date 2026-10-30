package org.openmf.training.data;

public record UpdateAccountNoRequest(
        Long clientId,
        Long productId,
        String accountNo
) {
}
