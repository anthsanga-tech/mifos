package org.openmf.training.service;

import org.openmf.training.data.CreateStandingInstructionRequest;
import org.openmf.training.data.StandingInstructionData;
import org.openmf.training.domain.SavingsAccount;
import org.openmf.training.domain.SavingsAccountStatus;
import org.openmf.training.domain.StandingInstruction;
import org.openmf.training.repository.SavingsAccountRepository;
import org.openmf.training.repository.StandingInstructionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

@Service
public class StandingInstructionWritePlatformService {

    private static final String ACTIVE = "ACTIVE";

    private final SavingsAccountRepository accountRepository;
    private final StandingInstructionRepository standingInstructionRepository;
    private final SavingsAccountAuditTrailWritePlatformService auditTrailService;

    public StandingInstructionWritePlatformService(
            SavingsAccountRepository accountRepository,
            StandingInstructionRepository standingInstructionRepository,
            SavingsAccountAuditTrailWritePlatformService auditTrailService
    ) {
        this.accountRepository = accountRepository;
        this.standingInstructionRepository = standingInstructionRepository;
        this.auditTrailService = auditTrailService;
    }

    @Transactional
    public StandingInstructionData create(
            Long fromAccountId,
            CreateStandingInstructionRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Standing instruction request is required");
        }
        if (request.toAccountId() == null) {
            throw new IllegalArgumentException("Destination savings account is required");
        }
        if (Objects.equals(fromAccountId, request.toAccountId())) {
            throw new IllegalArgumentException(
                    "Source and destination savings accounts must be different"
            );
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Standing instruction amount must be greater than zero");
        }
        if (request.amount().precision() > 19 || request.amount().scale() > 6) {
            throw new IllegalArgumentException(
                    "Standing instruction amount must fit DECIMAL(19,6)"
            );
        }
        if (request.frequency() == null || request.frequency().isBlank()
                || request.frequency().length() > 30) {
            throw new IllegalArgumentException(
                    "Frequency is required and must not exceed 30 characters"
            );
        }
        if (request.nextDue() == null || request.validFrom() == null) {
            throw new IllegalArgumentException("Next due date and valid-from date are required");
        }
        if (request.nextDue().isBefore(request.validFrom())) {
            throw new IllegalArgumentException("Next due date cannot be before valid-from date");
        }
        if (request.validTo() != null && request.validTo().isBefore(request.validFrom())) {
            throw new IllegalArgumentException("Valid-to date cannot be before valid-from date");
        }
        if (request.validTo() != null && request.nextDue().isAfter(request.validTo())) {
            throw new IllegalArgumentException("Next due date cannot be after valid-to date");
        }

        SavingsAccount fromAccount = findAccount(fromAccountId);
        SavingsAccount toAccount = findAccount(request.toAccountId());
        requireActive(fromAccount, "Source");
        requireActive(toAccount, "Destination");

        StandingInstruction instruction = new StandingInstruction();
        instruction.setFromAccount(fromAccount);
        instruction.setToAccount(toAccount);
        instruction.setAmount(request.amount());
        instruction.setFrequency(request.frequency().trim());
        instruction.setNextDue(request.nextDue());
        instruction.setStatus(ACTIVE);
        instruction.setValidFrom(request.validFrom());
        instruction.setValidTo(request.validTo());

        StandingInstruction saved = standingInstructionRepository.save(instruction);
        auditTrailService.record(
                fromAccount,
                "SI_CREATE",
                "SI_CREATE id=" + saved.getId()
                        + " toAccountId=" + toAccount.getId()
                        + " amount=" + saved.getAmount()
                        + " frequency=" + saved.getFrequency()
                        + " nextDue=" + saved.getNextDue(),
                saved.getAmount()
        );
        return toData(saved);
    }

    @Transactional
    public StandingInstructionData updateStatus(
            Long fromAccountId,
            Long instructionId,
            String action
    ) {
        StandingInstruction instruction = standingInstructionRepository.findById(instructionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Standing instruction not found: " + instructionId
                ));
        if (!instruction.getFromAccount().getId().equals(fromAccountId)) {
            throw new IllegalArgumentException(
                    "Standing instruction does not belong to savings account: " + fromAccountId
            );
        }

        String normalizedAction = action == null ? "" : action.toLowerCase(Locale.ROOT);
        String newStatus = switch (normalizedAction) {
            case "pause" -> {
                requireStatus(instruction, ACTIVE, "Only active standing instructions can be paused");
                yield "PAUSED";
            }
            case "resume" -> {
                requireStatus(instruction, "PAUSED", "Only paused standing instructions can be resumed");
                yield ACTIVE;
            }
            case "cancel" -> {
                if (!ACTIVE.equals(instruction.getStatus())
                        && !"PAUSED".equals(instruction.getStatus())) {
                    throw new IllegalArgumentException(
                            "Only active or paused standing instructions can be cancelled"
                    );
                }
                yield "CANCELLED";
            }
            default -> throw new IllegalArgumentException(
                    "Unknown standing instruction action: " + action
            );
        };

        instruction.setStatus(newStatus);
        StandingInstruction updated = standingInstructionRepository.save(instruction);
        String event = "SI_" + normalizedAction.toUpperCase(Locale.ROOT);
        auditTrailService.record(
                updated.getFromAccount(),
                event,
                event + " id=" + updated.getId()
                        + " status=" + updated.getStatus(),
                updated.getAmount()
        );
        return toData(updated);
    }

    private void requireStatus(
            StandingInstruction instruction,
            String expectedStatus,
            String errorMessage
    ) {
        if (!expectedStatus.equals(instruction.getStatus())) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private SavingsAccount findAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Savings account not found: " + accountId
                ));
    }

    private void requireActive(SavingsAccount account, String role) {
        if (account.getStatus() != SavingsAccountStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    role + " savings account must be ACTIVE"
            );
        }
    }

    private StandingInstructionData toData(StandingInstruction instruction) {
        return new StandingInstructionData(
                instruction.getId(),
                instruction.getFromAccount().getId(),
                instruction.getToAccount().getId(),
                instruction.getFromAccount().getAccountNo(),
                instruction.getToAccount().getAccountNo(),
                instruction.getAmount(),
                instruction.getFrequency(),
                instruction.getNextDue(),
                instruction.getStatus(),
                instruction.getValidFrom(),
                instruction.getValidTo()
        );
    }
}
