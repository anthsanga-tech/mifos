package org.openmf.training.data;

import java.time.LocalDate;
import java.util.List;

public record RunDueStandingInstructionsData(
        Long accountId,
        LocalDate businessDate,
        int processedCount,
        int skippedCount,
        int pendingCount,
        boolean pausedInstructionSkipped,
        String message,
        List<StandingInstructionRunData> runs
) {
}
