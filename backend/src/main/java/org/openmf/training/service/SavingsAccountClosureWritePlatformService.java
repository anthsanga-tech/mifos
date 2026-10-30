package org.openmf.training.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openmf.training.data.CloseSavingsAccountRequest;
import org.openmf.training.data.ClosureSettlementTransferPayload;
import org.openmf.training.data.SavingsAccountClosureDestinationData;
import org.openmf.training.data.SavingsAccountClosurePreviewData;
import org.openmf.training.data.SavingsAccountClosureResultData;
import org.openmf.training.data.SavingsTransactionData;
import org.openmf.training.data.TransferRequest;
import org.openmf.training.domain.FreezeStatus;
import org.openmf.training.domain.SavingsAccount;
import org.openmf.training.domain.SavingsAccountHoldStatus;
import org.openmf.training.domain.SavingsAccountStatus;
import org.openmf.training.domain.SavingsMakerChecker;
import org.openmf.training.domain.SavingsMakerCheckerCommandType;
import org.openmf.training.domain.SavingsMakerCheckerStatus;
import org.openmf.training.domain.SavingsTransaction;
import org.openmf.training.domain.SavingsTransactionType;
import org.openmf.training.domain.StandingInstruction;
import org.openmf.training.repository.SavingsAccountHoldRepository;
import org.openmf.training.repository.SavingsAccountRepository;
import org.openmf.training.repository.SavingsMakerCheckerRepository;
import org.openmf.training.repository.SavingsProductRepository;
import org.openmf.training.repository.SavingsTransactionRepository;
import org.openmf.training.repository.StandingInstructionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.math.BigDecimal.ZERO;

@Service
public class SavingsAccountClosureWritePlatformService {

    private static final short NOT_REVERSED = 0;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SavingsAccountRepository accountRepository;
    private final SavingsAccountHoldRepository holdRepository;
    private final SavingsMakerCheckerRepository makerCheckerRepository;
    private final SavingsProductRepository productRepository;
    private final SavingsTransactionRepository transactionRepository;
    private final StandingInstructionRepository standingInstructionRepository;
    private final SavingsAccountAuditTrailWritePlatformService auditTrailService;
    private final SavingsTransactionWritePlatformService transactionService;
    private final BigDecimal approvalThreshold;

    public SavingsAccountClosureWritePlatformService(
            SavingsAccountRepository accountRepository,
            SavingsAccountHoldRepository holdRepository,
            SavingsMakerCheckerRepository makerCheckerRepository,
            SavingsProductRepository productRepository,
            SavingsTransactionRepository transactionRepository,
            StandingInstructionRepository standingInstructionRepository,
            SavingsAccountAuditTrailWritePlatformService auditTrailService,
            SavingsTransactionWritePlatformService transactionService,
            @Value("${training.savings.maker-checker.threshold:10000}") BigDecimal approvalThreshold
    ) {
        this.accountRepository = accountRepository;
        this.holdRepository = holdRepository;
        this.makerCheckerRepository = makerCheckerRepository;
        this.productRepository = productRepository;
        this.transactionRepository = transactionRepository;
        this.standingInstructionRepository = standingInstructionRepository;
        this.auditTrailService = auditTrailService;
        this.transactionService = transactionService;
        this.approvalThreshold = approvalThreshold;
    }

    @Transactional(readOnly = true)
    public SavingsAccountClosurePreviewData preview(Long accountId) {
        return evaluate(findAccount(accountId), null);
    }

    @Transactional
    public SavingsAccountClosureResultData closeAndSettle(
            Long accountId,
            CloseSavingsAccountRequest request
    ) {
        SavingsAccount account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Savings account not found: " + accountId));
        SavingsAccountClosurePreviewData preview = evaluate(account, null);
        requireNoBlockers(preview);

        SavingsAccountClosureDestinationData destination = selectDestination(
                preview,
                request == null ? null : request.destinationAccountId()
        );
        if (preview.residualAmount().compareTo(approvalThreshold) >= 0) {
            SavingsMakerChecker pendingRequest = createPendingSettlement(
                    account,
                    destination,
                    preview.residualAmount(),
                    preview.closureFee()
            );
            auditTrailService.record(
                    account,
                    "CLOSE_SETTLEMENT_PENDING",
                    "CLOSE_SETTLEMENT_PENDING pendingId=" + pendingRequest.getId()
                            + " destination=" + destination.accountNo()
                            + " amount=" + preview.residualAmount(),
                    preview.residualAmount()
            );
            return new SavingsAccountClosureResultData(
                    "PENDING_APPROVAL",
                    "Settlement transfer submitted for approval. The account will close after approval.",
                    pendingRequest.getId(),
                    destination.accountId()
            );
        }

        String transactionGroupId = null;
        if (preview.residualAmount().signum() > 0) {
            SavingsTransactionData transfer = transactionService.transferForClosureSettlement(
                    new TransferRequest(account.getAccountNo(), destination.accountNo(), preview.residualAmount()),
                    "TRF-" + UUID.randomUUID()
            );
            transactionGroupId = transfer.transactionGroupId();
        }

        completeClosure(account, preview.closureFee(), transactionGroupId);
        return new SavingsAccountClosureResultData(
                "CLOSED",
                "The account was settled and closed.",
                null,
                destination == null ? null : destination.accountId()
        );
    }

    @Transactional(readOnly = true)
    public void validatePendingSettlementForApproval(
            Long accountId,
            Long destinationAccountId,
            BigDecimal amount,
            BigDecimal closureFee,
            Long makerCheckerId
    ) {
        if (closureFee == null || closureFee.signum() < 0) {
            throw new IllegalArgumentException("Closure fee must be zero or greater");
        }
        SavingsAccount account = findAccount(accountId);
        requireOpenUnfrozenAccount(account);
        SavingsAccountClosurePreviewData preview = evaluate(account, makerCheckerId);
        if (preview.activeHoldCount() > 0) {
            throw new IllegalArgumentException("Active holds prevent closure settlement approval");
        }
        if (preview.pendingMakerCheckerCount() > 0) {
            throw new IllegalArgumentException("Another pending maker-checker request prevents closure settlement approval");
        }
        if (amount == null || closureFee == null
                || account.getBalance().compareTo(amount.add(closureFee)) != 0) {
            throw new IllegalArgumentException(
                    "The source account balance changed after the settlement request was submitted"
            );
        }
        findEligibleDestination(account, destinationAccountId);
    }

    @Transactional
    public void chargeApprovedClosureFee(Long accountId, BigDecimal closureFee) {
        SavingsAccount account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Savings account not found: " + accountId));
        requireOpenUnfrozenAccount(account);
        if (closureFee == null || closureFee.signum() < 0
                || account.getBalance().compareTo(closureFee) < 0) {
            throw new IllegalArgumentException("Insufficient balance for the closure fee");
        }
        if (closureFee.signum() == 0) {
            return;
        }

        BigDecimal balanceAfterFee = account.getBalance().subtract(closureFee);
        account.setBalance(balanceAfterFee);
        accountRepository.save(account);

        SavingsTransaction fee = new SavingsTransaction();
        fee.setSavingsAccount(account);
        fee.setTransactionType(SavingsTransactionType.FEE);
        fee.setAmount(closureFee);
        fee.setTransactionDate(LocalDateTime.now());
        fee.setRunningBalance(balanceAfterFee);
        fee.setReversed(NOT_REVERSED);
        transactionRepository.save(fee);
        auditTrailService.record(
                account,
                "CLOSURE_FEE",
                "CLOSURE_FEE amount=" + closureFee,
                closureFee
        );
    }

    @Transactional
    public void completeApprovedSettlement(
            Long accountId,
            Long destinationAccountId,
            BigDecimal closureFee,
            Long makerCheckerId,
            String transactionGroupId
    ) {
        SavingsAccount account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Savings account not found: " + accountId));
        requireOpenUnfrozenAccount(account);
        if (makerCheckerRepository.countBySavingsAccountIdAndStatusAndIdNot(
                accountId, SavingsMakerCheckerStatus.PENDING, makerCheckerId) > 0) {
            throw new IllegalArgumentException("Another pending maker-checker request prevents closure");
        }
        if (holdRepository.countBySavingsAccountIdAndStatus(accountId, SavingsAccountHoldStatus.ACTIVE) > 0) {
            throw new IllegalArgumentException("Active holds prevent closure");
        }
        findEligibleDestination(account, destinationAccountId);
        if (account.getBalance().compareTo(ZERO) != 0) {
            throw new IllegalArgumentException(
                    "The settlement must transfer the full post-fee balance before closure"
            );
        }
        account.setStatus(SavingsAccountStatus.CLOSED);
        accountRepository.save(account);
        cancelOpenStandingInstructions(account);
        auditTrailService.record(
                account,
                "CLOSED",
                "CLOSED after settlement pendingId=" + makerCheckerId
                        + " transferGroupId=" + transactionGroupId,
                null
        );
    }

    private SavingsMakerChecker createPendingSettlement(
            SavingsAccount account,
            SavingsAccountClosureDestinationData destination,
            BigDecimal amount,
            BigDecimal closureFee
    ) {
        ClosureSettlementTransferPayload payload = new ClosureSettlementTransferPayload(
                account.getAccountNo(),
                destination.accountNo(),
                amount,
                closureFee,
                true
        );
        String payloadJson;
        try {
            payloadJson = OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize closure settlement request", exception);
        }

        SavingsMakerChecker request = new SavingsMakerChecker();
        request.setSavingsAccount(account);
        request.setCommandType(SavingsMakerCheckerCommandType.TRANSFER);
        request.setPayloadJson(payloadJson);
        request.setAmount(amount);
        request.setStatus(SavingsMakerCheckerStatus.PENDING);
        request.setMadeBy(currentActor());
        request.setMadeOn(LocalDateTime.now());
        return makerCheckerRepository.save(request);
    }

    private void completeClosure(SavingsAccount account, BigDecimal closureFee, String transactionGroupId) {
        BigDecimal balanceAfterFee = account.getBalance().subtract(closureFee);
        if (balanceAfterFee.signum() < 0) {
            throw new IllegalArgumentException("Insufficient balance for the closure fee");
        }

        if (closureFee.signum() > 0) {
            SavingsTransaction fee = new SavingsTransaction();
            fee.setSavingsAccount(account);
            fee.setTransactionType(SavingsTransactionType.FEE);
            fee.setAmount(closureFee);
            fee.setTransactionDate(LocalDateTime.now());
            fee.setRunningBalance(balanceAfterFee);
            fee.setReversed(NOT_REVERSED);
            transactionRepository.save(fee);
            auditTrailService.record(
                    account,
                    "CLOSURE_FEE",
                    "CLOSURE_FEE amount=" + closureFee,
                    closureFee
            );
        }
        account.setBalance(balanceAfterFee);
        account.setStatus(SavingsAccountStatus.CLOSED);
        accountRepository.save(account);
        cancelOpenStandingInstructions(account);
        auditTrailService.record(
                account,
                "CLOSED",
                "CLOSED after settlement"
                        + " transferGroupId=" + (transactionGroupId == null ? "-" : transactionGroupId),
                null
        );
    }

    private void cancelOpenStandingInstructions(SavingsAccount account) {
        Map<Long, StandingInstruction> instructions = new LinkedHashMap<>();
        standingInstructionRepository.findByFromAccountIdForUpdate(account.getId())
                .forEach(instruction -> instructions.put(instruction.getId(), instruction));
        standingInstructionRepository.findByToAccountIdForUpdate(account.getId())
                .forEach(instruction -> instructions.put(instruction.getId(), instruction));

        for (StandingInstruction instruction : instructions.values()) {
            if ("ACTIVE".equalsIgnoreCase(instruction.getStatus())
                    || "PAUSED".equalsIgnoreCase(instruction.getStatus())) {
                instruction.setStatus("CANCELLED");
                standingInstructionRepository.save(instruction);
                auditTrailService.record(
                        instruction.getFromAccount(),
                        "SI_CANCEL",
                        "SI_CANCEL id=" + instruction.getId() + " reason=account closed",
                        instruction.getAmount()
                );
            }
        }
    }

    private SavingsAccountClosurePreviewData evaluate(SavingsAccount account, Long excludedMakerCheckerId) {
        BigDecimal balance = account.getBalance() == null ? ZERO : account.getBalance();
        BigDecimal activeHoldAmount = holdRepository.sumAmountByAccountIdAndStatus(
                account.getId(), SavingsAccountHoldStatus.ACTIVE);
        if (activeHoldAmount == null) {
            activeHoldAmount = ZERO;
        }
        long activeHoldCount = holdRepository.countBySavingsAccountIdAndStatus(
                account.getId(), SavingsAccountHoldStatus.ACTIVE);
        BigDecimal minimumRequiredBalance = account.getProduct().getMinRequiredBalance() == null
                ? ZERO
                : account.getProduct().getMinRequiredBalance();
        BigDecimal availableBalance = balance.subtract(activeHoldAmount).subtract(minimumRequiredBalance).max(ZERO);
        BigDecimal configuredClosureFee = productRepository.findClosureFeeById(account.getProduct().getId())
                .orElse(null);
        BigDecimal closureFee = configuredClosureFee == null ? ZERO : configuredClosureFee;
        BigDecimal residualAmount = balance.subtract(closureFee).max(ZERO);
        boolean frozen = account.getFreezeStatus() == FreezeStatus.FROZEN;
        long pendingCount = excludedMakerCheckerId == null
                ? makerCheckerRepository.countBySavingsAccountIdAndStatus(
                        account.getId(), SavingsMakerCheckerStatus.PENDING)
                : makerCheckerRepository.countBySavingsAccountIdAndStatusAndIdNot(
                        account.getId(), SavingsMakerCheckerStatus.PENDING, excludedMakerCheckerId);

        List<SavingsAccountClosureDestinationData> destinations = eligibleDestinations(account);
        long openStandingInstructions = findOpenStandingInstructions(account).size();
        List<String> blockers = new ArrayList<>();
        if (closureFee.signum() < 0) {
            blockers.add("Closure fee cannot be negative.");
        }
        if (configuredClosureFee == null) {
            blockers.add("Could not load the savings product closure fee; refresh or contact support.");
        }
        if (account.getStatus() != SavingsAccountStatus.ACTIVE) {
            blockers.add("Account must be ACTIVE to close.");
        }
        if (frozen) {
            blockers.add("Account is FROZEN — unfreeze before close.");
        }
        if (activeHoldCount > 0) {
            blockers.add("Active holds: " + activeHoldCount + " — release holds first.");
        }
        if (pendingCount > 0) {
            blockers.add("Pending maker-checker requests: " + pendingCount
                    + " — approve or reject pending items before closing.");
        }
        if (residualAmount.add(closureFee).compareTo(balance) > 0) {
            blockers.add("Insufficient balance for settlement (residual="
                    + residualAmount.toPlainString() + ", fee=" + closureFee.toPlainString()
                    + ", balance=" + balance.toPlainString() + ").");
        }
        if (residualAmount.signum() > 0 && destinations.isEmpty()) {
            blockers.add("An ACTIVE sibling account is required to receive the residual balance.");
        }

        return new SavingsAccountClosurePreviewData(
                blockers.isEmpty(),
                balance,
                availableBalance,
                closureFee,
                residualAmount,
                activeHoldAmount,
                activeHoldCount,
                openStandingInstructions,
                pendingCount,
                frozen,
                residualAmount.compareTo(approvalThreshold) >= 0,
                destinations,
                blockers
        );
    }

    private List<SavingsAccountClosureDestinationData> eligibleDestinations(SavingsAccount account) {
        return accountRepository.findByClientIdOrderByIdAsc(account.getClient().getId())
                .stream()
                .filter(candidate -> !candidate.getId().equals(account.getId()))
                .filter(candidate -> candidate.getStatus() == SavingsAccountStatus.ACTIVE)
                .filter(candidate -> candidate.getFreezeStatus() != FreezeStatus.FROZEN)
                .map(candidate -> new SavingsAccountClosureDestinationData(
                        candidate.getId(),
                        candidate.getAccountNo(),
                        candidate.getBalance()
                ))
                .toList();
    }

    private SavingsAccountClosureDestinationData selectDestination(
            SavingsAccountClosurePreviewData preview,
            Long requestedDestinationId
    ) {
        if (preview.residualAmount().signum() == 0) {
            return null;
        }
        List<SavingsAccountClosureDestinationData> destinations = preview.eligibleDestinations();
        if (requestedDestinationId == null && destinations.size() == 1) {
            return destinations.get(0);
        }
        if (requestedDestinationId == null) {
            throw new IllegalArgumentException("Choose a sibling ACTIVE account for the settlement balance");
        }
        return destinations.stream()
                .filter(destination -> destination.accountId().equals(requestedDestinationId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Settlement destination must be an unfrozen ACTIVE sibling account"));
    }

    private SavingsAccountClosureDestinationData findEligibleDestination(
            SavingsAccount source,
            Long destinationAccountId
    ) {
        return eligibleDestinations(source).stream()
                .filter(destination -> destination.accountId().equals(destinationAccountId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Settlement destination must be an unfrozen ACTIVE sibling account"));
    }

    private List<StandingInstruction> findOpenStandingInstructions(SavingsAccount account) {
        Map<Long, StandingInstruction> instructions = new LinkedHashMap<>();
        standingInstructionRepository.findByFromAccountIdOrderByIdAsc(account.getId())
                .forEach(instruction -> instructions.put(instruction.getId(), instruction));
        standingInstructionRepository.findByToAccountIdOrderByIdAsc(account.getId())
                .forEach(instruction -> instructions.put(instruction.getId(), instruction));
        return instructions.values().stream()
                .filter(instruction -> "ACTIVE".equalsIgnoreCase(instruction.getStatus())
                        || "PAUSED".equalsIgnoreCase(instruction.getStatus()))
                .toList();
    }

    private void requireNoBlockers(SavingsAccountClosurePreviewData preview) {
        if (!preview.canClose()) {
            throw new IllegalArgumentException(String.join(" ", preview.blockers()));
        }
    }

    private void requireOpenUnfrozenAccount(SavingsAccount account) {
        if (account.getStatus() != SavingsAccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Account must be ACTIVE to close");
        }
        if (account.getFreezeStatus() == FreezeStatus.FROZEN) {
            throw new IllegalArgumentException("Unfreeze the account before closing");
        }
    }

    private SavingsAccount findAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Savings account not found: " + accountId));
    }

    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            return "system";
        }
        return authentication.getName();
    }
}
