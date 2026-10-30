package org.openmf.training.service;

import org.openmf.training.data.StandingInstructionData;
import org.openmf.training.data.StandingInstructionRunData;
import org.openmf.training.domain.StandingInstruction;
import org.openmf.training.domain.StandingInstructionRun;
import org.openmf.training.repository.SavingsAccountRepository;
import org.openmf.training.repository.StandingInstructionRepository;
import org.openmf.training.repository.StandingInstructionRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StandingInstructionReadPlatformService {

    private final SavingsAccountRepository accountRepository;
    private final StandingInstructionRepository standingInstructionRepository;
    private final StandingInstructionRunRepository runRepository;

    public StandingInstructionReadPlatformService(
            SavingsAccountRepository accountRepository,
            StandingInstructionRepository standingInstructionRepository,
            StandingInstructionRunRepository runRepository
    ) {
        this.accountRepository = accountRepository;
        this.standingInstructionRepository = standingInstructionRepository;
        this.runRepository = runRepository;
    }

    @Transactional(readOnly = true)
    public List<StandingInstructionData> retrieveByFromAccount(Long accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new IllegalArgumentException("Savings account not found: " + accountId);
        }

        return standingInstructionRepository
                .findByFromAccountIdOrderByIdAsc(accountId)
                .stream()
                .map(this::toData)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StandingInstructionRunData> retrieveRuns(Long accountId, Long instructionId) {
        if (!accountRepository.existsById(accountId)) {
            throw new IllegalArgumentException("Savings account not found: " + accountId);
        }

        StandingInstruction instruction = standingInstructionRepository.findById(instructionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Standing instruction not found: " + instructionId));
        if (!instruction.getFromAccount().getId().equals(accountId)) {
            throw new IllegalArgumentException(
                    "Standing instruction does not belong to savings account: " + accountId);
        }

        return runRepository.findByStandingInstruction_IdOrderByBusinessDateDescRanOnDesc(instructionId)
                .stream()
                .map(this::toRunData)
                .toList();
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

    private StandingInstructionRunData toRunData(StandingInstructionRun run) {
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
