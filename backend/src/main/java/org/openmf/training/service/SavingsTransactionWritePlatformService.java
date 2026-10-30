package org.openmf.training.service;

import jakarta.persistence.EntityNotFoundException;
import org.openmf.training.data.*;
import org.openmf.training.domain.*;
import org.openmf.training.exception.ResourceNotFoundException;
import org.openmf.training.repository.SavingsAccountHoldRepository;
import org.openmf.training.repository.SavingsAccountRepository;
import org.openmf.training.repository.SavingsTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static java.math.BigDecimal.ZERO;

@Service
public class SavingsTransactionWritePlatformService {

    private final SavingsTransactionRepository transactionRepository;
    private final SavingsAccountRepository accountRepository;
    private final SavingsAccountHoldRepository accountHoldRepository;
    private final SavingsAccountAuditTrailWritePlatformService auditTrailService;

    private static final short NOT_REVERSED = 0;
    private static final short REVERSED = 1;

    private static final BigDecimal DAYS_IN_YEAR = new BigDecimal("36500");
    private static final String INTEREST_METHOD = "DAILY_END_OF_DAY";
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    public SavingsTransactionWritePlatformService(
            SavingsTransactionRepository transactionRepository,
            SavingsAccountRepository accountRepository,
            SavingsAccountHoldRepository accountHoldRepository,
            SavingsAccountAuditTrailWritePlatformService auditTrailService
    ) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.accountHoldRepository = accountHoldRepository;
        this.auditTrailService = auditTrailService;
    }

    @Transactional(readOnly = true)
    public List<SavingsTransactionData> retrieveByAccount(
            Long accountId
    ) {
        return transactionRepository
                .findBySavingsAccountIdOrderByTransactionDateDescIdDesc(accountId)
                .stream()
                .map(this::toData)
                .toList();
    }

    @Transactional
    public SavingsTransactionData create(
            CreateSavingsTransactionRequest request
    ) {
        SavingsAccount account = accountRepository
                .findById(request.savingsAccountId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Savings account not found: "
                                        + request.savingsAccountId()
                        )
                );

        if (!SavingsAccountStatus.ACTIVE.equals(account.getStatus())) {
            throw new IllegalArgumentException(
                    "Transactions are only allowed on ACTIVE accounts"
            );
        }

        if (request.amount() == null || request.amount().compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Transaction amount must be greater than zero"
            );
        }

        SavingsTransactionType type = request.transactionType();

        if (!SavingsTransactionType.DEPOSIT.equals(type)
                && !SavingsTransactionType.WITHDRAWAL.equals(type)) {
            throw new IllegalArgumentException(
                    "Transaction type must be DEPOSIT or WITHDRAWAL"
            );
        }

        BigDecimal currentBalance = account.getBalance();
        LocalDateTime transactionDate = LocalDateTime.now();
        BigDecimal newBalance;
        BigDecimal withdrawalFee = ZERO;
        String transactionGroupId = null;

        if (SavingsTransactionType.DEPOSIT.equals(type)) {
            newBalance = currentBalance.add(request.amount());
        } else {
            withdrawalFee = account.getProduct().getWithdrawalFee();
            BigDecimal availableBalance =
                    account.getAvailableBalanceForWithdrawal(getActiveHoldAmount(account.getId()));
            if (availableBalance.compareTo(request.amount()) < 0) {
                throw new IllegalArgumentException(
                        "Insufficient available balance"
                );
            }
            transactionGroupId = "WDR-" + UUID.randomUUID();
            newBalance = currentBalance.subtract(request.amount()).subtract(withdrawalFee);
        }
        account.setBalance(newBalance);
        accountRepository.save(account);

        SavingsTransaction transaction = new SavingsTransaction();
        transaction.setSavingsAccount(account);
        transaction.setTransactionType(request.transactionType());
        transaction.setAmount(request.amount());
        transaction.setTransactionDate(transactionDate);
        transaction.setRunningBalance(
                SavingsTransactionType.WITHDRAWAL.equals(type)
                        ? currentBalance.subtract(request.amount())
                        : newBalance
        );
        transaction.setReversed(NOT_REVERSED);
        transaction.setTransactionGroupId(transactionGroupId);

        SavingsTransaction savedTransaction = transactionRepository.save(transaction);

        auditTrailService.record(
                account,
                type.name(),
                type.name() + " amount=" + request.amount(),
                request.amount()
        );

        if (SavingsTransactionType.WITHDRAWAL.equals(type)) {
            SavingsTransaction feeTransaction = new SavingsTransaction();
            feeTransaction.setSavingsAccount(account);
            feeTransaction.setTransactionType(SavingsTransactionType.FEE);
            feeTransaction.setAmount(withdrawalFee);
            feeTransaction.setTransactionDate(transactionDate);
            feeTransaction.setRunningBalance(newBalance);
            feeTransaction.setReversed(NOT_REVERSED);
            feeTransaction.setTransactionGroupId(transactionGroupId);
            transactionRepository.save(feeTransaction);
            auditTrailService.record(
                    account,
                    SavingsTransactionType.FEE.name(),
                    "FEE amount=" + withdrawalFee,
                    withdrawalFee
            );
        }

        return toData(savedTransaction);
    }

    private BigDecimal getActiveHoldAmount(Long accountId) {
        BigDecimal holdAmount = accountHoldRepository.sumAmountByAccountIdAndStatus(accountId, SavingsAccountHoldStatus.ACTIVE);

        return holdAmount != null
                ? holdAmount
                : ZERO;
    }

    @Transactional
    public SavingsTransactionData undoTransaction(
            Long accountId,
            Long transactionId) {

        SavingsAccount account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Savings account not found"
                ));
        if (account.getStatus() == SavingsAccountStatus.CLOSED) {
            throw new IllegalStateException("Transactions on a closed savings account cannot be undone");
        }

        SavingsTransaction transaction = transactionRepository
                .findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Savings transaction not found"
                ));

        // Make sure transaction belongs to this account
        if (!transaction.getSavingsAccount()
                .getId()
                .equals(accountId)) {

            throw new IllegalArgumentException(
                    "Transaction does not belong to this savings account"
            );
        }

        // Already reversed
        if (transaction.getReversed() != null
                && transaction.getReversed() == 1) {

            throw new IllegalStateException(
                    "Transaction has already been reversed"
            );
        }

        SavingsTransactionType type =
                transaction.getTransactionType();

        if (type == SavingsTransactionType.DEPOSIT
                || type == SavingsTransactionType.INTEREST
                || type == SavingsTransactionType.WITHDRAWAL
                || type == SavingsTransactionType.FEE) {

            SavingsTransactionData result = undoNormalTransaction(
                    account,
                    transaction
            );
            auditTrailService.record(
                    account,
                    "UNDO",
                    "UNDO originalType=" + type + " amount=" + result.amount(),
                    result.amount()
            );
            return result;
        }

        if (type == SavingsTransactionType.TRANSFER_IN
                || type == SavingsTransactionType.TRANSFER_OUT) {
            return undoTransfer(transaction);
        }

        throw new IllegalArgumentException(
                "Invalid transaction type"
        );
    }

    private SavingsTransactionData undoNormalTransaction(
            SavingsAccount account,
            SavingsTransaction transaction) {
        SavingsTransaction withdrawal = transaction;
        SavingsTransaction fee = null;
        String groupId = transaction.getTransactionGroupId();

        if (groupId != null && !groupId.isBlank()
                && (transaction.getTransactionType() == SavingsTransactionType.WITHDRAWAL
                || transaction.getTransactionType() == SavingsTransactionType.FEE)) {
            List<SavingsTransaction> group = transactionRepository.findByTransactionGroupId(groupId);
            if (transaction.getTransactionType() == SavingsTransactionType.FEE) {
                fee = transaction;
                withdrawal = group.stream()
                        .filter(t -> t.getTransactionType() == SavingsTransactionType.WITHDRAWAL)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "Withdrawal transaction not found for fee"
                        ));
            } else {
                fee = group.stream()
                        .filter(t -> t.getTransactionType() == SavingsTransactionType.FEE)
                        .findFirst()
                        .orElse(null);
            }
        }

        if (withdrawal != transaction || fee != null) {
            if (!withdrawal.getSavingsAccount().getId().equals(account.getId())
                    || !fee.getSavingsAccount().getId().equals(account.getId())) {
                throw new IllegalStateException("Withdrawal and fee transactions do not match");
            }
            if (isReversed(withdrawal) || isReversed(fee)) {
                throw new IllegalStateException("Withdrawal has already been reversed");
            }

            BigDecimal amount = withdrawal.getAmount().add(fee.getAmount());
            fee.setReversed(REVERSED);
            transactionRepository.save(fee);
            withdrawal.setReversed(REVERSED);
            transactionRepository.save(withdrawal);

            BigDecimal newBalance = account.getBalance().add(amount);
            account.setBalance(newBalance);
            accountRepository.save(account);

            return toData(createCompensatingTransaction(
                    account,
                    SavingsTransactionType.DEPOSIT,
                    amount,
                    newBalance,
                    null,
                    LocalDateTime.now()
            ));
        }

        SavingsTransactionType compensatingType = switch (transaction.getTransactionType()) {
            case DEPOSIT, INTEREST -> SavingsTransactionType.WITHDRAWAL;
            case WITHDRAWAL, FEE -> SavingsTransactionType.DEPOSIT;
            default -> throw new IllegalArgumentException("Invalid transaction type");
        };
        BigDecimal amount = transaction.getAmount();
        BigDecimal newBalance;
        if (compensatingType == SavingsTransactionType.WITHDRAWAL) {
            BigDecimal availableBalance =
                    account.getAvailableBalance(getActiveHoldAmount(account.getId()));
            if (availableBalance.compareTo(amount) < 0) {
                throw new IllegalStateException(
                        "Cannot reverse transaction because of insufficient available balance"
                );
            }
            newBalance = account.getBalance().subtract(amount);
        } else {
            newBalance = account.getBalance().add(amount);
        }

        transaction.setReversed(REVERSED);
        transactionRepository.save(transaction);
        account.setBalance(newBalance);
        accountRepository.save(account);

        return toData(createCompensatingTransaction(
                account,
                compensatingType,
                amount,
                newBalance,
                null,
                LocalDateTime.now()
        ));
    }

    private boolean isReversed(SavingsTransaction transaction) {
        return transaction.getReversed() != null
                && transaction.getReversed() == REVERSED;
    }

    private SavingsTransaction createCompensatingTransaction(
            SavingsAccount account,
            SavingsTransactionType type,
            BigDecimal amount,
            BigDecimal runningBalance,
            String transactionGroupId,
            LocalDateTime transactionDate) {
        SavingsTransaction compensation = new SavingsTransaction();
        compensation.setSavingsAccount(account);
        compensation.setTransactionType(type);
        compensation.setAmount(amount);
        compensation.setTransactionDate(transactionDate);
        compensation.setRunningBalance(runningBalance);
        compensation.setReversed(NOT_REVERSED);
        compensation.setTransactionGroupId(transactionGroupId);
        return transactionRepository.save(compensation);
    }

    @Transactional(readOnly = true)
    public SavingsInterestPreviewData previewInterest(Long accountId, SavingsInterestRequest request) {
        SavingsAccount account = findAccount(accountId);
        return calculateInterest(account, request);
    }

    @Transactional
    public SavingsTransactionData postInterest(Long accountId, SavingsInterestRequest request) {
        SavingsAccount account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Savings account not found: " + accountId));
        SavingsInterestPreviewData preview = calculateInterest(account, request);

        BigDecimal updatedBalance = account.getBalance().add(preview.amount());
        account.setBalance(updatedBalance);
        accountRepository.save(account);

        SavingsTransaction transaction = new SavingsTransaction();
        transaction.setSavingsAccount(account);
        transaction.setTransactionType(SavingsTransactionType.INTEREST);
        transaction.setAmount(preview.amount());
        transaction.setTransactionDate(LocalDateTime.now());
        transaction.setRunningBalance(updatedBalance);
        transaction.setReversed(NOT_REVERSED);
        transaction.setInterestPeriodFrom(preview.fromDate());
        transaction.setInterestPeriodTo(preview.toDate());
        transactionRepository.save(transaction);

        auditTrailService.record(
                account,
                "INTEREST_POST",
                "INTEREST_POST amount=" + preview.amount()
                        + " method=" + preview.calculationMethod()
                        + " averageDailyBalance=" + preview.averageDailyBalance()
                        + " fromDate=" + preview.fromDate()
                        + " toDate=" + preview.toDate()
                        + " days=" + preview.days(),
                preview.amount()
        );
        return toData(transaction);
    }

    private SavingsInterestPreviewData calculateInterest(SavingsAccount account, SavingsInterestRequest request) {
        if (account.getStatus() != SavingsAccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Interest can only be posted to an ACTIVE account");
        }
        if (account.getFreezeStatus() == FreezeStatus.FROZEN) {
            throw new IllegalArgumentException("Interest cannot be posted to a frozen account");
        }
        BigDecimal rate = account.getProduct().getNominalAnnualInterestRate();
        if (rate == null || rate.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("Product annual interest rate must be greater than zero");
        }
        if (request == null || request.fromDate() == null || request.toDate() == null) {
            throw new IllegalArgumentException("fromDate and toDate are required in YYYY-MM-DD format");
        }

        LocalDate fromDate = parseInterestDate("fromDate", request.fromDate());
        LocalDate toDate = parseInterestDate("toDate", request.toDate());
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate must be on or before toDate");
        }
        if (toDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("toDate cannot be in the future");
        }
        if (account.getActivatedOn() == null) {
            throw new IllegalStateException("Savings account activation date is unavailable");
        }
        if (fromDate.isBefore(account.getActivatedOn().toLocalDate())) {
            throw new IllegalArgumentException("fromDate cannot be before the account activation date");
        }

        long dayCount = ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        if (dayCount > 366) {
            throw new IllegalArgumentException("Interest period cannot exceed 366 days");
        }
        if (transactionRepository
                .existsBySavingsAccountIdAndTransactionTypeAndReversedAndInterestPeriodFromLessThanEqualAndInterestPeriodToGreaterThanEqual(
                        account.getId(), SavingsTransactionType.INTEREST, NOT_REVERSED, toDate, fromDate)) {
            throw new IllegalArgumentException(
                    "Interest period overlaps an existing non-reversed INTEREST transaction; undo that INTEREST or pick another range"
            );
        }

        List<SavingsTransaction> transactions = transactionRepository
                .findBySavingsAccountIdOrderByTransactionDateAscIdAsc(account.getId());
        List<SavingsAccountHold> holds = accountHoldRepository
                .findBySavingsAccountIdOrderByCreatedOnDesc(account.getId());
        BigDecimal dailyRate = rate.divide(DAYS_IN_YEAR, MathContext.DECIMAL128);
        BigDecimal balanceSum = ZERO;
        BigDecimal interestSum = ZERO;
        int transactionIndex = 0;
        BigDecimal endOfDayBalance = ZERO;

        for (LocalDate day = fromDate; !day.isAfter(toDate); day = day.plusDays(1)) {
            LocalDateTime nextDay = day.plusDays(1).atStartOfDay();
            while (transactionIndex < transactions.size()) {
                SavingsTransaction transaction = transactions.get(transactionIndex);
                if (!transaction.getTransactionDate().isBefore(nextDay)) {
                    break;
                }
                if (transaction.getReversed() != null && transaction.getReversed() == NOT_REVERSED) {
                    endOfDayBalance = transaction.getRunningBalance();
                }
                transactionIndex++;
            }

            BigDecimal activeHoldAmount = holds.stream()
                    .filter(hold -> hold.getCreatedOn().isBefore(nextDay)
                            && (hold.getReleasedOn() == null || !hold.getReleasedOn().isBefore(nextDay)))
                    .map(SavingsAccountHold::getAmount)
                    .reduce(ZERO, BigDecimal::add);
            BigDecimal endOfDayAvailableBalance =
                    account.getAvailableBalance(endOfDayBalance, activeHoldAmount);
            balanceSum = balanceSum.add(endOfDayAvailableBalance);
            interestSum = interestSum.add(endOfDayAvailableBalance.multiply(dailyRate));
        }

        BigDecimal amount = interestSum.setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("Interest amount is zero");
        }
        BigDecimal averageDailyBalance = balanceSum
                .divide(BigDecimal.valueOf(dayCount), 2, RoundingMode.HALF_UP);
        return new SavingsInterestPreviewData(
                fromDate,
                toDate,
                Math.toIntExact(dayCount),
                averageDailyBalance,
                amount,
                INTEREST_METHOD,
                rate
        );
    }

    private LocalDate parseInterestDate(String field, String value) {
        if (!ISO_DATE_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a valid ISO date in YYYY-MM-DD format");
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(field + " must be a valid ISO date in YYYY-MM-DD format");
        }
    }

    private SavingsTransactionData undoTransfer(
            SavingsTransaction transaction) {

        String groupId = transaction.getTransactionGroupId();

        if (groupId == null || groupId.isBlank()) {
            throw new IllegalStateException(
                    "Transfer transaction has no transaction group"
            );
        }

        List<SavingsTransaction> transactions =
                transactionRepository.findByTransactionGroupId(groupId);

        if (transactions.size() != 2) {
            throw new IllegalStateException(
                    "Invalid transfer transaction group"
            );
        }

        SavingsTransaction transferOut = transactions.stream()
                .filter(t -> t.getTransactionType()
                        == SavingsTransactionType.TRANSFER_OUT)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "TRANSFER_OUT transaction not found"
                ));

        SavingsTransaction transferIn = transactions.stream()
                .filter(t -> t.getTransactionType()
                        == SavingsTransactionType.TRANSFER_IN)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "TRANSFER_IN transaction not found"
                ));

        if (isReversed(transferOut) || isReversed(transferIn)) {
            throw new IllegalStateException(
                    "Transfer has already been reversed"
            );
        }

        BigDecimal amount = transferOut.getAmount();

        if (amount.compareTo(transferIn.getAmount()) != 0) {
            throw new IllegalStateException(
                    "Transfer amounts do not match"
            );
        }

        SavingsAccount fromAccount =
                transferOut.getSavingsAccount();

        SavingsAccount toAccount =
                transferIn.getSavingsAccount();
        if (fromAccount.getStatus() == SavingsAccountStatus.CLOSED
                || toAccount.getStatus() == SavingsAccountStatus.CLOSED) {
            throw new IllegalStateException("Transfers involving a closed savings account cannot be undone");
        }

        // ---------------------------------------
        // Undo TRANSFER_OUT
        // Source gets money back
        // ---------------------------------------

        BigDecimal fromNewBalance =
                fromAccount.getBalance().add(amount);

        // ---------------------------------------
        // Undo TRANSFER_IN
        // Destination loses the money
        // ---------------------------------------

        BigDecimal availableBalance = toAccount.getAvailableBalance(getActiveHoldAmount(toAccount.getId()));

        if (availableBalance.compareTo(amount) < 0) {
            throw new IllegalStateException(
                    "Cannot reverse transfer because of insufficient available balance"
            );
        }

        BigDecimal toNewBalance = toAccount.getBalance().subtract(amount);

        fromAccount.setBalance(fromNewBalance);
        toAccount.setBalance(toNewBalance);
        transferOut.setReversed(REVERSED);
        transferIn.setReversed(REVERSED);

        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        transactionRepository.save(transferOut);
        transactionRepository.save(transferIn);

        LocalDateTime now = LocalDateTime.now();
        String reversalGroupId = "REV-TRF-" + UUID.randomUUID();
        SavingsTransaction compensatingTransferIn = createCompensatingTransaction(
                fromAccount,
                SavingsTransactionType.TRANSFER_IN,
                amount,
                fromNewBalance,
                reversalGroupId,
                now
        );
        SavingsTransaction compensatingTransferOut = createCompensatingTransaction(
                toAccount,
                SavingsTransactionType.TRANSFER_OUT,
                amount,
                toNewBalance,
                reversalGroupId,
                now
        );
        auditTrailService.record(
                fromAccount,
                "UNDO",
                "UNDO originalType=TRANSFER_OUT amount=" + amount,
                amount
        );
        auditTrailService.record(
                toAccount,
                "UNDO",
                "UNDO originalType=TRANSFER_IN amount=" + amount,
                amount
        );

        return toData(transaction.getTransactionType() == SavingsTransactionType.TRANSFER_OUT
                ? compensatingTransferIn
                : compensatingTransferOut);
    }

    @Transactional
    public SavingsTransactionData transfer(TransferRequest request) {
        return performTransfer(request, "TRF-" + UUID.randomUUID());
    }

    @Transactional
    public SavingsTransactionData transfer(
            TransferRequest request,
            String transactionGroupId
    ) {
        if (transactionGroupId == null || transactionGroupId.isBlank()
                || transactionGroupId.length() > 100) {
            throw new IllegalArgumentException("Transfer group ID is required and must not exceed 100 characters");
        }
        return performTransfer(request, transactionGroupId);
    }

    @Transactional
    public SavingsTransactionData transferForClosureSettlement(
            TransferRequest request,
            String transactionGroupId
    ) {
        if (transactionGroupId == null || !transactionGroupId.startsWith("TRF-")
                || transactionGroupId.length() > 100) {
            throw new IllegalArgumentException("Closure transfer group ID must use the TRF-<UUID> format");
        }
        try {
            UUID.fromString(transactionGroupId.substring("TRF-".length()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Closure transfer group ID must use the TRF-<UUID> format", exception);
        }
        return performTransfer(request, transactionGroupId, true);
    }

    private SavingsTransactionData performTransfer(
            TransferRequest request,
            String transactionGroupId
    ) {
        return performTransfer(request, transactionGroupId, false);
    }

    private SavingsTransactionData performTransfer(
            TransferRequest request,
            String transactionGroupId,
            boolean closingSourceAccount
    ) {
        if (request.fromAccountNo().equals(request.toAccountNo())) {
            throw new IllegalArgumentException(
                    "Source and destination accounts must be different"
            );
        }

        if (request.amount() == null ||
                request.amount().compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Transfer amount must be greater than zero"
            );
        }

        SavingsAccount fromAccount = accountRepository
                .findByAccountNo(request.fromAccountNo())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Source account not found: " + request.fromAccountNo()
                ));

        SavingsAccount toAccount = accountRepository
                .findByAccountNo(request.toAccountNo())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Destination account not found: " + request.toAccountNo()
                ));

        validateAccount(fromAccount, "Source");
        validateAccount(toAccount, "Destination");

        BigDecimal amount = request.amount();

        BigDecimal activeHolds = getActiveHoldAmount(fromAccount.getId());
        BigDecimal availableBalance = closingSourceAccount
                ? fromAccount.getBalance().subtract(activeHolds)
                : fromAccount.getAvailableBalance(activeHolds);

        if (availableBalance.compareTo(amount) < 0) {
            throw new IllegalArgumentException(
                    "Insufficient available balance"
            );
        }

        /*
         * One group ID represents one transfer operation.
         * Both TRANSFER_OUT and TRANSFER_IN use the same ID.
         */
        LocalDateTime now = LocalDateTime.now();

        // -----------------------------------
        // Source account
        // -----------------------------------

        BigDecimal fromNewBalance =
                fromAccount.getBalance().subtract(amount);

        fromAccount.setBalance(fromNewBalance);

        SavingsTransaction transferOut = new SavingsTransaction();

        transferOut.setSavingsAccount(fromAccount);
        transferOut.setTransactionType(
                SavingsTransactionType.TRANSFER_OUT
        );
        transferOut.setAmount(amount);
        transferOut.setTransactionDate(now);
        transferOut.setRunningBalance(fromNewBalance);
        transferOut.setReversed((short) 0);
        transferOut.setTransactionGroupId(transactionGroupId);

        // -----------------------------------
        // Destination account
        // -----------------------------------

        BigDecimal toNewBalance = toAccount.getBalance().add(amount);

        toAccount.setBalance(toNewBalance);

        SavingsTransaction transferIn = new SavingsTransaction();

        transferIn.setSavingsAccount(toAccount);
        transferIn.setTransactionType(SavingsTransactionType.TRANSFER_IN);
        transferIn.setAmount(amount);
        transferIn.setTransactionDate(now);
        transferIn.setRunningBalance(toNewBalance);
        transferIn.setReversed((short) 0);
        transferIn.setTransactionGroupId(transactionGroupId);

        // Save accounts
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        // Save transaction history
        transactionRepository.save(transferOut);
//        transactionRepository.save(transferIn);
        auditTrailService.record(
                fromAccount,
                SavingsTransactionType.TRANSFER_OUT.name(),
                "TRANSFER_OUT amount=" + amount,
                amount
        );
        auditTrailService.record(
                toAccount,
                SavingsTransactionType.TRANSFER_IN.name(),
                "TRANSFER_IN amount=" + amount,
                amount
        );

        return  toData(transactionRepository.save(transferIn));
    }

    private SavingsAccount findAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));
    }

    private void validateAccount(
            SavingsAccount account,
            String accountType
    ) {
        if (!"ACTIVE".equals(account.getStatus().name())) {
            throw new IllegalArgumentException(
                    accountType + " account must be ACTIVE"
            );
        }
    }

    private SavingsTransactionData toData(
            SavingsTransaction transaction
    ) {
        return new SavingsTransactionData(
                transaction.getId(),
                transaction.getSavingsAccount().getId(),
                transaction.getTransactionType(),
                transaction.getAmount(),
                transaction.getTransactionDate(),
                transaction.getRunningBalance(),
                transaction.getReversed(),
                transaction.getTransactionGroupId(),
                transaction.getInterestPeriodFrom(),
                transaction.getInterestPeriodTo(),
                isUndoableWhileAccountOpen(transaction)
        );
    }

    private boolean isUndoableWhileAccountOpen(SavingsTransaction transaction) {
        if (transaction.getSavingsAccount().getStatus() == SavingsAccountStatus.CLOSED) {
            return false;
        }
        if (transaction.getTransactionType() != SavingsTransactionType.TRANSFER_IN
                && transaction.getTransactionType() != SavingsTransactionType.TRANSFER_OUT) {
            return true;
        }

        String groupId = transaction.getTransactionGroupId();
        if (groupId == null || groupId.isBlank()) {
            return false;
        }
        List<SavingsTransaction> transferGroup = transactionRepository.findByTransactionGroupId(groupId);
        return transferGroup.size() == 2
                && transferGroup.stream().noneMatch(groupTransaction ->
                groupTransaction.getSavingsAccount().getStatus() == SavingsAccountStatus.CLOSED);
    }
}