package org.openmf.training.data;

import java.time.LocalDateTime;

public record SavingsAccountNoteData(
        Long id,
        Long savingsAccountId,
        String note,
        String by,
        LocalDateTime when
) {
}
