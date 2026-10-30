package org.openmf.training.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openmf.training.data.CreateSavingsAccountsCheckMakerRequest;
import org.openmf.training.data.ClosureSettlementTransferPayload;
import org.openmf.training.data.SavingsMakerCheckerData;
import org.openmf.training.data.TransferRequest;
import org.openmf.training.domain.SavingsAccount;
import org.openmf.training.domain.SavingsAccountHoldStatus;
import org.openmf.training.domain.SavingsAccountStatus;
import org.openmf.training.domain.SavingsMakerChecker;
import org.openmf.training.domain.SavingsMakerCheckerCommandType;
import org.openmf.training.domain.SavingsMakerCheckerStatus;
import org.openmf.training.domain.SavingsTransaction;
import org.openmf.training.domain.SavingsTransactionType;
import org.openmf.training.domain.StandingInstructionRun;
import org.openmf.training.repository.SavingsAccountHoldRepository;
import org.openmf.training.repository.SavingsAccountRepository;
import org.openmf.training.repository.SavingsMakerCheckerRepository;
import org.openmf.training.repository.SavingsTransactionRepository;
import org.openmf.training.repository.StandingInstructionRunRepository;
import org.openmf.training.service.SavingsAccountClosureWritePlatformService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static java.math.BigDecimal.ZERO;

@Service
public class SavingsAccountsCheckMakerWritePlatformService {

    private static final short NOT_REVERSED = 0;
    private static final int MAX_REJECT_REASON_LENGTH = 500;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SavingsMakerCheckerRepository checkMakerRepository;
    private final SavingsAccountRepository accountRepository;
    private final SavingsTransactionRepository transactionRepository;
    private final SavingsAccountHoldRepository holdRepository;
    private final SavingsAccountAuditTrailWritePlatformService auditTrailService;
    private final SavingsTransactionWritePlatformService transactionService;
    private final StandingInstructionRunRepository standingInstructionRunRepository;
    private final SavingsAccountClosureWritePlatformService closureService;
    private final BigDecimal approvalThreshold;

    public SavingsAccountsCheckMakerWritePlatformService(
            SavingsMakerCheckerRepository checkMakerRepository,
            SavingsAccountRepository accountRepository,
            SavingsTransactionRepository transactionRepository,
            SavingsAccountHoldRepository holdRepository,
            SavingsAccountAuditTrailWritePlatformService auditTrailService,
            SavingsTransactionWritePlatformService transactionService,
            StandingInstructionRunRepository standingInstructionRunRepository,
            SavingsAccountClosureWritePlatformService closureService,
            @Value("${training.savings.maker-checker.threshold:10000}") BigDecimal approvalThreshold
    ) {
        this.checkMakerRepository = checkMakerRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.holdRepository = holdRepository;
        this.auditTrailService = auditTrailService;
        this.transactionService = transactionService;
        this.standingInstructionRunRepository = standingInstructionRunRepository;
        this.closureService = closureService;
        this.approvalThreshold = approvalThreshold;
    }

    private ClosureSettlementTransferPayload closureSettlementPayload(String payloadJson) {
        try {
            return OBJECT_MAPPER.readValue(payloadJson, ClosureSettlementTransferPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Pending closure settlement has an invalid payload", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<SavingsMakerCheckerData> retrieve(SavingsMakerCheckerStatus status, Long accountId) {
        List<SavingsMakerChecker> requests;
        if (accountId != null && status != null) {
            requests = checkMakerRepository
                    .findBySavingsAccountIdAndStatusOrderByMadeOnDescIdDesc(accountId, status);
        } else if (accountId != null) {
            requests = checkMakerRepository.findBySavingsAccountIdOrderByMadeOnDescIdDesc(accountId);
        } else if (status != null) {
            requests = checkMakerRepository.findByStatusOrderByMadeOnDescIdDesc(status);
        } else {
            requests = checkMakerRepository.findAllByOrderByMadeOnDescIdDesc();
        }
        return requests.stream().map(this::toData).toList();
    }

    @Transactional
    public SavingsMakerCheckerData create(CreateSavingsAccountsCheckMakerRequest request) {
        if (request == null || request.savingsAccountId() == null) {
            throw new IllegalArgumentException("savingsAccountId is required");
        }
        if (request.transactionType() == null) {
            throw new IllegalArgumentException("transactionType is required");
        }
        if (request.transactionType() == SavingsMakerCheckerCommandType.TRANSFER) {
            throw new IllegalArgumentException(
                    "Transfer approvals must be created by a standing instruction");
        }
        requirePositiveAmount(request.amount());
        if (request.amount().compareTo(approvalThreshold) < 0) {
            throw new IllegalArgumentException(
                    "Amounts below the maker-checker threshold must execute immediately");
        }

        SavingsAccount account = requireAccount(request.savingsAccountId());
        requireActiveAccount(account);
        SavingsMakerChecker checkMaker = new SavingsMakerChecker();
        checkMaker.setSavingsAccount(account);
        checkMaker.setCommandType(request.transactionType());
        checkMaker.setPayloadJson("{\"amount\":" + request.amount().toPlainString() + "}");
        checkMaker.setAmount(request.amount());
        checkMaker.setStatus(SavingsMakerCheckerStatus.PENDING);
        checkMaker.setMadeBy(currentActor());
        checkMaker.setMadeOn(LocalDateTime.now());
        SavingsMakerChecker savedRequest = checkMakerRepository.save(checkMaker);
        auditTrailService.record(
                account,
                "MAKER_SUBMIT",
                "MAKER_SUBMIT pendingId=" + savedRequest.getId()
                        + " type=" + savedRequest.getCommandType()
                        + " amount=" + savedRequest.getAmount(),
                savedRequest.getAmount()
        );
        return toData(savedRequest);
    }

    @Transactional
    public SavingsMakerCheckerData approve(Long requestId) {
        SavingsMakerChecker checkMaker = findPendingRequest(requestId);
        String checker = currentActor();
        requireDifferentMaker(checkMaker, checker, "approve");

        SavingsAccount account = accountRepository.findByIdForUpdate(checkMaker.getSavingsAccount().getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Savings account not found: " + checkMaker.getSavingsAccount().getId()));
        requireActiveAccount(account);
        if (checkMaker.getCommandType() == SavingsMakerCheckerCommandType.TRANSFER) {
            var standingInstructionRun = standingInstructionRunRepository.findByMakerCheckerId(checkMaker.getId());
            if (standingInstructionRun.isPresent()) {
                StandingInstructionRun run = standingInstructionRun.get();
                var transfer = transactionService.transfer(
                        transferRequest(checkMaker.getPayloadJson()),
                        "TRF-" + UUID.randomUUID()
                );
                run.setStatus("SUCCESS");
                run.setReason(null);
                run.setTransferGroupId(transfer.transactionGroupId());
                standingInstructionRunRepository.save(run);
                auditTrailService.record(
                        run.getStandingInstruction().getFromAccount(),
                        "SI_EXECUTE",
                        "SI_EXECUTE id=" + run.getStandingInstruction().getId()
                                + " due=" + run.getDueDate()
                                + " SUCCESS groupId=" + run.getTransferGroupId(),
                        run.getStandingInstruction().getAmount()
                );
            } else {
                ClosureSettlementTransferPayload payload = closureSettlementPayload(checkMaker.getPayloadJson());
                if (!payload.closureSettlement()
                        || !account.getAccountNo().equals(payload.fromAccountNo())
                        || payload.amount() == null
                        || checkMaker.getAmount() == null
                        || checkMaker.getAmount().compareTo(payload.amount()) != 0) {
                    throw new IllegalStateException("Pending transfer is not linked to a standing instruction or closure settlement");
                }
                SavingsAccount destinationAccount = accountRepository.findByAccountNo(payload.toAccountNo())
                        .orElseThrow(() -> new IllegalArgumentException("Settlement destination account not found"));
                closureService.validatePendingSettlementForApproval(
                        account.getId(),
                        destinationAccount.getId(),
                        payload.amount(),
                        payload.closureFee(),
                        checkMaker.getId()
                );
                checkMaker.setStatus(SavingsMakerCheckerStatus.APPROVED);
                checkMaker.setCheckedBy(checker);
                checkMaker.setCheckedOn(LocalDateTime.now());
                checkMakerRepository.save(checkMaker);
                auditTrailService.record(
                        account,
                        "CHECKER_APPROVE",
                        "CHECKER_APPROVE pendingId=" + checkMaker.getId()
                                + " type=" + checkMaker.getCommandType()
                                + " amount=" + checkMaker.getAmount(),
                        checkMaker.getAmount()
                );
                closureService.chargeApprovedClosureFee(account.getId(), payload.closureFee());
                String groupId = "TRF-" + UUID.randomUUID();
                transactionService.transferForClosureSettlement(payload.toTransferRequest(), groupId);
                closureService.completeApprovedSettlement(
                        account.getId(),
                        destinationAccount.getId(),
                        payload.closureFee(),
                        checkMaker.getId(),
                        groupId
                );
                return toData(checkMaker);
            }
        } else {
            postTransaction(account, checkMaker.getCommandType(), checkMaker.getAmount());
        }

        checkMaker.setStatus(SavingsMakerCheckerStatus.APPROVED);
        checkMaker.setCheckedBy(checker);
        checkMaker.setCheckedOn(LocalDateTime.now());
        SavingsMakerChecker savedRequest = checkMakerRepository.save(checkMaker);
        auditTrailService.record(
                account,
                "CHECKER_APPROVE",
                "CHECKER_APPROVE pendingId=" + savedRequest.getId()
                        + " type=" + savedRequest.getCommandType()
                        + " amount=" + savedRequest.getAmount(),
                savedRequest.getAmount()
        );
        return toData(savedRequest);
    }

    private TransferRequest transferRequest(String payloadJson) {
        try {
            return OBJECT_MAPPER.readValue(payloadJson, TransferRequest.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Pending transfer approval has an invalid payload", exception);
        }
    }

    @Transactional
    public SavingsMakerCheckerData reject(Long requestId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A rejection reason is required");
        }
        String normalizedReason = reason.trim();
        if (normalizedReason.length() > MAX_REJECT_REASON_LENGTH) {
            throw new IllegalArgumentException(
                    "Rejection reason must not exceed " + MAX_REJECT_REASON_LENGTH + " characters");
        }
        SavingsMakerChecker checkMaker = findPendingRequest(requestId);
        String checker = currentActor();
        requireDifferentMaker(checkMaker, checker, "reject");
        checkMaker.setStatus(SavingsMakerCheckerStatus.REJECTED);
        checkMaker.setCheckedBy(checker);
        checkMaker.setCheckedOn(LocalDateTime.now());
        checkMaker.setRejectReason(normalizedReason);
        if (checkMaker.getCommandType() == SavingsMakerCheckerCommandType.TRANSFER) {
            var standingInstructionRun = standingInstructionRunRepository.findByMakerCheckerId(checkMaker.getId());
            if (standingInstructionRun.isPresent()) {
                StandingInstructionRun run = standingInstructionRun.get();
                run.setStatus("REJECT");
                run.setReason(normalizedReason);
                standingInstructionRunRepository.save(run);
            } else if (!closureSettlementPayload(checkMaker.getPayloadJson()).closureSettlement()) {
                throw new IllegalStateException("Pending transfer is not linked to a standing instruction or closure settlement");
            }
        }
        SavingsMakerChecker savedRequest = checkMakerRepository.save(checkMaker);
        auditTrailService.record(
                checkMaker.getSavingsAccount(),
                "CHECKER_REJECT",
                "CHECKER_REJECT pendingId=" + savedRequest.getId()
                        + " type=" + savedRequest.getCommandType()
                        + " amount=" + savedRequest.getAmount()
                        + " reason=" + normalizedReason,
                savedRequest.getAmount()
        );
        return toData(savedRequest);
    }

    @Transactional
    public SavingsMakerCheckerData cancel(Long requestId) {
        SavingsMakerChecker checkMaker = findPendingRequest(requestId);
        if (!currentActor().equals(checkMaker.getMadeBy())) {
            throw new IllegalArgumentException("Only the maker can cancel their request");
        }
        checkMaker.setStatus(SavingsMakerCheckerStatus.CANCELLED);
        SavingsMakerChecker savedRequest = checkMakerRepository.save(checkMaker);
        auditTrailService.record(
                checkMaker.getSavingsAccount(),
                "CHECKER_CANCEL",
                "CHECKER_CANCEL pendingId=" + savedRequest.getId()
                        + " type=" + savedRequest.getCommandType()
                        + " amount=" + savedRequest.getAmount(),
                savedRequest.getAmount()
        );
        return toData(savedRequest);
    }

    private void postTransaction(
            SavingsAccount account,
            SavingsMakerCheckerCommandType transactionType,
            BigDecimal amount
    ) {
        BigDecimal currentBalance = account.getBalance();
        BigDecimal updatedBalance;
        String transactionGroupId = null;
        BigDecimal fee = ZERO;
        if (transactionType == SavingsMakerCheckerCommandType.WITHDRAWAL) {
            fee = account.getProduct().getWithdrawalFee();
            BigDecimal activeHolds = holdRepository.sumAmountByAccountIdAndStatus(
                    account.getId(), SavingsAccountHoldStatus.ACTIVE);
            if (account.getAvailableBalanceForWithdrawal(activeHolds).compareTo(amount) < 0) {
                throw new IllegalArgumentException("Insufficient available balance");
            }
            transactionGroupId = "WDR-" + UUID.randomUUID();
            updatedBalance = currentBalance.subtract(amount).subtract(fee);
        } else {
            updatedBalance = currentBalance.add(amount);
        }
        account.setBalance(updatedBalance);
        accountRepository.save(account);

        SavingsTransaction transaction = new SavingsTransaction();
        transaction.setSavingsAccount(account);
        transaction.setTransactionType(toTransactionType(transactionType));
        transaction.setAmount(amount);
        transaction.setTransactionDate(LocalDateTime.now());
        transaction.setRunningBalance(transactionType == SavingsMakerCheckerCommandType.WITHDRAWAL
                ? currentBalance.subtract(amount) : updatedBalance);
        transaction.setReversed(NOT_REVERSED);
        transaction.setTransactionGroupId(transactionGroupId);
        transactionRepository.save(transaction);
        SavingsTransactionType postedType = toTransactionType(transactionType);
        auditTrailService.record(
                account,
                postedType.name(),
                postedType.name() + " amount=" + amount,
                amount
        );

        if (fee.signum() > 0) {
            SavingsTransaction feeTransaction = new SavingsTransaction();
            feeTransaction.setSavingsAccount(account);
            feeTransaction.setTransactionType(SavingsTransactionType.FEE);
            feeTransaction.setAmount(fee);
            feeTransaction.setTransactionDate(transaction.getTransactionDate());
            feeTransaction.setRunningBalance(updatedBalance);
            feeTransaction.setReversed(NOT_REVERSED);
            feeTransaction.setTransactionGroupId(transactionGroupId);
            transactionRepository.save(feeTransaction);
        }
    }

    private SavingsTransactionType toTransactionType(SavingsMakerCheckerCommandType transactionType) {
        return switch (transactionType) {
            case DEPOSIT -> SavingsTransactionType.DEPOSIT;
            case WITHDRAWAL -> SavingsTransactionType.WITHDRAWAL;
            case INTEREST -> SavingsTransactionType.INTEREST;
            case TRANSFER -> throw new IllegalArgumentException(
                    "Transfer requests must be approved through transfer processing");
        };
    }

    private SavingsMakerChecker findPendingRequest(Long requestId) {
        SavingsMakerChecker checkMaker = checkMakerRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Check-maker request not found: " + requestId));
        if (checkMaker.getStatus() != SavingsMakerCheckerStatus.PENDING) {
            throw new IllegalArgumentException("Only PENDING requests can be changed");
        }
        return checkMaker;
    }

    private SavingsAccount requireAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Savings account not found: " + accountId));
    }

    private void requireActiveAccount(SavingsAccount account) {
        if (account.getStatus() != SavingsAccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Transactions are only allowed on ACTIVE accounts");
        }
    }

    private void requirePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction amount must be greater than zero");
        }
    }

    private void requireDifferentMaker(SavingsMakerChecker request, String checker, String action) {
        if (checker.equals(request.getMadeBy())) {
            throw new IllegalArgumentException("The maker cannot " + action + " their own request");
        }
    }

    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            return "system";
        }
        return authentication.getName();
    }

    private SavingsMakerCheckerData toData(SavingsMakerChecker request) {
        return new SavingsMakerCheckerData(
                request.getId(), request.getSavingsAccount().getAccountNo(), request.getCommandType(),
                request.getPayloadJson(), request.getAmount(), request.getStatus(), request.getMadeBy(),
                request.getMadeOn(), request.getCheckedBy(), request.getCheckedOn(), request.getRejectReason());
    }
}
