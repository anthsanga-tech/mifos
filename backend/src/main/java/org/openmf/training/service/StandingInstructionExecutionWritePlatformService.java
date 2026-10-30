package org.openmf.training.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openmf.training.data.RunDueStandingInstructionsData;
import org.openmf.training.data.StandingInstructionRunData;
import org.openmf.training.data.TransferRequest;
import org.openmf.training.domain.SavingsMakerChecker;
import org.openmf.training.domain.SavingsMakerCheckerCommandType;
import org.openmf.training.domain.SavingsMakerCheckerStatus;
import org.openmf.training.domain.StandingInstruction;
import org.openmf.training.domain.StandingInstructionRun;
import org.openmf.training.repository.SavingsAccountRepository;
import org.openmf.training.repository.SavingsMakerCheckerRepository;
import org.openmf.training.repository.StandingInstructionRepository;
import org.openmf.training.repository.StandingInstructionRunRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class StandingInstructionExecutionWritePlatformService {

    private static final String ACTIVE = "ACTIVE";
    private static final String PAUSED = "PAUSED";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SavingsAccountRepository accountRepository;
    private final SavingsAccountAuditTrailWritePlatformService auditTrailService;
    private final SavingsMakerCheckerRepository makerCheckerRepository;
    private final StandingInstructionRepository standingInstructionRepository;
    private final StandingInstructionRunRepository runRepository;
    private final SavingsTransactionWritePlatformService transactionService;
    private final BigDecimal approvalThreshold;

    public StandingInstructionExecutionWritePlatformService(
            SavingsAccountRepository accountRepository,
            SavingsAccountAuditTrailWritePlatformService auditTrailService,
            SavingsMakerCheckerRepository makerCheckerRepository,
            StandingInstructionRepository standingInstructionRepository,
            StandingInstructionRunRepository runRepository,
            SavingsTransactionWritePlatformService transactionService,
            @Value("${training.savings.maker-checker.threshold:10000}") BigDecimal approvalThreshold
    ) {
        this.accountRepository = accountRepository;
        this.auditTrailService = auditTrailService;
        this.makerCheckerRepository = makerCheckerRepository;
        this.standingInstructionRepository = standingInstructionRepository;
        this.runRepository = runRepository;
        this.transactionService = transactionService;
        this.approvalThreshold = approvalThreshold;
    }

    @Transactional
    public RunDueStandingInstructionsData runDue(Long accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new IllegalArgumentException("Savings account not found: " + accountId);
        }

        LocalDate businessDate = LocalDate.now();
        List<StandingInstructionRunData> runs = new ArrayList<>();
        boolean pausedInstructionSkipped = false;

        for (StandingInstruction instruction
                : standingInstructionRepository.findByFromAccountIdForUpdate(accountId)) {
            if (instruction.getNextDue().isAfter(businessDate)) {
                continue;
            }

            if (PAUSED.equalsIgnoreCase(instruction.getStatus())) {
                pausedInstructionSkipped = true;
                continue;
            }

            if (runRepository.existsByStandingInstruction_IdAndBusinessDate(instruction.getId(), businessDate)) {
                continue;
            }

            LocalDate dueDate = instruction.getNextDue();
            StandingInstructionRun run = new StandingInstructionRun();
            run.setStandingInstruction(instruction);
            run.setDueDate(dueDate);
            run.setBusinessDate(businessDate);
            run.setRanOn(LocalDateTime.now());

            if (instruction.getValidTo() != null
                    && instruction.getValidTo().isBefore(businessDate)) {
                run.setStatus("SKIPPED");
                run.setReason("Standing instruction validity period has ended");
            } else if (!ACTIVE.equalsIgnoreCase(instruction.getStatus())) {
                run.setStatus("SKIPPED");
                run.setReason("Standing instruction is not active");
            } else if (instruction.getAmount().compareTo(approvalThreshold) >= 0) {
                SavingsMakerChecker request = createTransferApproval(instruction);
                run.setStatus("PENDING_APPROVAL");
                run.setReason("Amount requires maker-checker; pendingId=" + request.getId());
                run.setMakerCheckerId(request.getId());
                advanceNextDue(instruction, dueDate, businessDate);
            } else {
                String transferGroupId = "TRF-" + UUID.randomUUID().toString();
                var transfer = transactionService.transfer(
                        new TransferRequest(
                                instruction.getFromAccount().getAccountNo(),
                                instruction.getToAccount().getAccountNo(),
                                instruction.getAmount()
                        ),
                        transferGroupId
                );
                run.setStatus("SUCCESS");
                run.setTransferGroupId(transfer.transactionGroupId());
                advanceNextDue(instruction, dueDate, businessDate);
            }

            StandingInstructionRun savedRun = runRepository.saveAndFlush(run);
            if ("SUCCESS".equals(savedRun.getStatus())
                    || "PENDING_APPROVAL".equals(savedRun.getStatus())) {
                String executionResult = "SUCCESS".equals(savedRun.getStatus())
                        ? "SUCCESS groupId=" + savedRun.getTransferGroupId()
                        : "PENDING_APPROVAL pendingId=" + savedRun.getMakerCheckerId();
                auditTrailService.record(
                        instruction.getFromAccount(),
                        "SI_EXECUTE",
                        "SI_EXECUTE id=" + instruction.getId()
                                + " due=" + savedRun.getDueDate()
                                + " " + executionResult,
                        instruction.getAmount()
                );
            }
            runs.add(toData(savedRun));
        }

        int successCount = (int) runs.stream()
                .filter(run -> "SUCCESS".equals(run.status()))
                .count();
        int skippedCount = (int) runs.stream()
                .filter(run -> "SKIPPED".equals(run.status()))
                .count();
        int pendingCount = (int) runs.stream()
                .filter(run -> "PENDING_APPROVAL".equals(run.status()))
                .count();
        int processedCount = successCount + skippedCount + pendingCount;
        String message = String.format(
                    "Run-due %s: processed %d (success %d, skipped %d, failed 0, pending %d).",
                    businessDate,
                    processedCount,
                    successCount,
                    skippedCount,
                    pendingCount
            );
        // String message;
        // if (pausedInstructionSkipped) {
        //     message = String.format(
        //             "Run-due %s: processed 0 ( success 0, skipped 0, failed 0, pending 0) ",
        //             businessDate
        //     );
        // } else if (runs.isEmpty()) {
        //     message = String.format(
        //             "Run-due %s: processed %d ( success 0, skipped 0, failed 0, pending %d)",
        //             businessDate,
        //             processedCount,
        //             pendingCount
        //     );
        // } else {
        //     message = String.format(
        //             "Run-due %s: processed %d (success %d, skipped %d, failed 0, pending %d).",
        //             businessDate,
        //             processedCount,
        //             successCount,
        //             skippedCount,
        //             pendingCount
        //     );
        // }

        return new RunDueStandingInstructionsData(
                accountId,
                businessDate,
                processedCount,
                skippedCount,
                pendingCount,
                pausedInstructionSkipped,
                message,
                List.copyOf(runs)
        );
    }

    private SavingsMakerChecker createTransferApproval(StandingInstruction instruction) {
        SavingsMakerChecker request = new SavingsMakerChecker();
        request.setSavingsAccount(instruction.getFromAccount());
        request.setCommandType(SavingsMakerCheckerCommandType.TRANSFER);
        request.setPayloadJson(serializeTransfer(new TransferRequest(
                instruction.getFromAccount().getAccountNo(),
                instruction.getToAccount().getAccountNo(),
                instruction.getAmount()
        )));
        request.setAmount(instruction.getAmount());
        request.setStatus(SavingsMakerCheckerStatus.PENDING);
        request.setMadeBy(currentActor());
        request.setMadeOn(LocalDateTime.now());
        SavingsMakerChecker savedRequest = makerCheckerRepository.saveAndFlush(request);
        auditTrailService.record(
                instruction.getFromAccount(),
                "MAKER_SUBMIT",
                "MAKER_SUBMIT pendingId=" + savedRequest.getId()
                        + " type=TRANSFER amount=" + savedRequest.getAmount(),
                savedRequest.getAmount()
        );
        return savedRequest;
    }

    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            return "system";
        }
        return authentication.getName();
    }

    private String serializeTransfer(TransferRequest request) {
        try {
            return OBJECT_MAPPER.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize standing instruction transfer approval", exception);
        }
    }

    private void advanceNextDue(StandingInstruction instruction, LocalDate dueDate, LocalDate businessDate) {
        instruction.setNextDue(nextDueAfterBusinessDate(
                instruction.getFrequency(),
                dueDate,
                businessDate
        ));
        standingInstructionRepository.save(instruction);
    }

    private LocalDate nextDueAfterBusinessDate(
            String frequency,
            LocalDate dueDate,
            LocalDate businessDate
    ) {
        String normalizedFrequency = frequency.toUpperCase(Locale.ROOT);
        LocalDate nextDue = dueDate;
        do {
            nextDue = switch (normalizedFrequency) {
                case "DAILY", "DAY" -> nextDue.plusDays(1);
                case "WEEKLY", "WEEK" -> nextDue.plusWeeks(1);
                case "MONTHLY", "MONTH" -> nextDue.plusMonths(1);
                case "QUARTERLY" -> nextDue.plusMonths(3);
                case "YEARLY", "ANNUALLY", "YEAR" -> nextDue.plusYears(1);
                default -> throw new IllegalArgumentException(
                        "Unsupported standing instruction frequency: " + frequency
                );
            };
        } while (!nextDue.isAfter(businessDate));
        return nextDue;
    }

    private StandingInstructionRunData toData(StandingInstructionRun run) {
        return new StandingInstructionRunData(
                run.getId(),
                run.getStandingInstruction().getId(),
                run.getDueDate(),
                run.getBusinessDate(),
                run.getStatus(),
                run.getReason(),
                run.getTransferGroupId(),
                run.getMakerCheckerId(),
                run.getRanOn()
        );
    }
}
