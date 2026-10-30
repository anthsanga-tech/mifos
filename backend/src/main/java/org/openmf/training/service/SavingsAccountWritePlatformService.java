package org.openmf.training.service;

import org.openmf.training.data.*;
import org.openmf.training.domain.*;
import org.openmf.training.exception.ResourceNotFoundException;
import org.openmf.training.repository.ClientRepository;
import org.openmf.training.repository.SavingsAccountHoldRepository;
import org.openmf.training.repository.SavingsAccountRepository;
import org.openmf.training.repository.SavingsProductRepository;
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
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class SavingsAccountWritePlatformService {

    private final SavingsAccountRepository accountRepository;
    private final SavingsProductRepository productRepository;
    private final ClientRepository clientRepository;
    private final SavingsAccountHoldRepository savingsAccountHoldRepository;
    private final SavingsAccountAuditTrailWritePlatformService auditTrailService;


    public SavingsAccountWritePlatformService(
            SavingsAccountRepository accountRepository,
            SavingsProductRepository productRepository,
            ClientRepository clientRepository,
            SavingsAccountHoldRepository savingsAccountHoldRepository,
            SavingsAccountAuditTrailWritePlatformService auditTrailService) {
        this.accountRepository = accountRepository;
        this.productRepository = productRepository;
        this.clientRepository = clientRepository;
        this.savingsAccountHoldRepository = savingsAccountHoldRepository;
        this.auditTrailService = auditTrailService;
    }

    @Transactional(readOnly = true)
    public List<SavingsAccountData> retrieveAll() {
        return accountRepository.findAll()
                .stream()
                .map(this::toData)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SavingsAccountHoldData> retrieveHolds(Long accountId) {

        if (!accountRepository.existsById(accountId)) {
            throw new IllegalArgumentException(
                    "Savings account not found"
            );
        }

        return savingsAccountHoldRepository
                .findBySavingsAccountIdOrderByCreatedOnDesc(accountId)
                .stream()
                .map(this::toHoldData)
                .toList();
    }

    @Transactional
    public SavingsAccountData create(
            CreateSavingsAccountRequest request
    ) {

        if (accountRepository.existsByAccountNo(request.accountNo())) {
            throw new IllegalArgumentException(
                    "Savings account number is already in use: " + request.accountNo()
            );
        }

        if (!productRepository.existsById(request.productId())) {
            throw new IllegalArgumentException(
                    "Savings product not found: " + request.productId()
            );
        }

        Client client = findClient(request.clientId());
        SavingsProduct product = findProduct(request.productId());

        SavingsAccount account = new SavingsAccount();
        account.setClient(client);
        account.setProduct(product);
        account.setAccountNo(request.accountNo());
        account.setStatus(SavingsAccountStatus.SUBMITTED);
        account.setCreatedOn(LocalDateTime.now());

        SavingsAccount savedAccount = accountRepository.save(account);
        auditTrailService.record(
                savedAccount,
                "CREATE",
                "CREATE account " + savedAccount.getAccountNo(),
                null
        );
        return toData(savedAccount);
    }

    @Transactional
    public SavingsAccountHoldData placeHold(Long accountId, CreateSavingsAccountHoldRequest request){
        SavingsAccount account = findAccount(accountId);

        // 1. Account must be ACTIVE
        if (account.getStatus() != SavingsAccountStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "Hold can only be placed on an ACTIVE account"
            );
        }

        // 2. Account must not be frozen
        if (account.getFreezeStatus() == FreezeStatus.FROZEN) {
            throw new IllegalArgumentException(
                    "Cannot place hold on a frozen account"
            );
        }

        // 3. Amount must be positive
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Hold amount must be greater than zero"
            );
        }

        // 4. Calculate available balance
        BigDecimal activeHoldAmount = savingsAccountHoldRepository.sumAmountByAccountIdAndStatus(accountId, SavingsAccountHoldStatus.ACTIVE);

        if (activeHoldAmount == null) {
            activeHoldAmount = BigDecimal.ZERO;
        }

        BigDecimal availableBalance = account.getAvailableBalance(activeHoldAmount);

        // 5. Check sufficient available balance
        if (request.amount().compareTo(availableBalance) > 0) {
            throw new IllegalArgumentException(
                    "Insufficient available balance"
            );
        }

        // 6. Create hold
        SavingsAccountHold hold = new SavingsAccountHold();
        hold.setSavingsAccount(account);
        hold.setAmount(request.amount());
        hold.setReason(
                request.reason() == null || request.reason().trim().isEmpty()
                        ? null
                        : request.reason().trim()
        );
        hold.setStatus(SavingsAccountHoldStatus.ACTIVE);
        hold.setCreatedOn(LocalDateTime.now());
        hold = savingsAccountHoldRepository.save(hold);
        auditTrailService.record(
                account,
                "HOLD_PLACE",
                "HOLD_PLACE amount=" + hold.getAmount(),
                hold.getAmount()
        );

        return toHoldData(hold);
    }

    @Transactional
    public SavingsAccountHoldData releaseHold(Long accountId, Long holdId) {

        SavingsAccountHold hold = findHoldAccount(holdId);

        // Make sure hold belongs to this account
        if (!hold.getSavingsAccount().getId().equals(accountId)) {
            throw new IllegalArgumentException(
                    "Hold does not belong to this account"
            );
        }

        // Cannot release twice
        if (hold.getStatus() == SavingsAccountHoldStatus.RELEASED) {
            throw new IllegalArgumentException(
                    "Hold is already released"
            );
        }

        hold.setStatus(SavingsAccountHoldStatus.RELEASED);
        hold.setReleasedOn(LocalDateTime.now());
        hold = savingsAccountHoldRepository.save(hold);
        auditTrailService.record(
                hold.getSavingsAccount(),
                "HOLD_RELEASE",
                "HOLD_RELEASE amount=" + hold.getAmount(),
                hold.getAmount()
        );

        return toHoldData(hold);
    }

    @Transactional
    public SavingsAccountData updateAccountNo(Long accountId, UpdateAccountNoRequest updateAccountNoRequest) {
        if (!accountRepository.existsById(accountId)) {
            throw new IllegalArgumentException(
                    "Savings account not found: " + accountId
            );
        }
        SavingsAccount savingsAccount = findAccount(accountId);
        String previousAccountNo = savingsAccount.getAccountNo();
        savingsAccount.setAccountNo(updateAccountNoRequest.accountNo());
        SavingsAccount savedAccount = accountRepository.save(savingsAccount);
        auditTrailService.record(
                savedAccount,
                "ACCOUNT_NO_UPDATE",
                "ACCOUNT_NO_UPDATE " + previousAccountNo + " -> " + savedAccount.getAccountNo(),
                null
        );
        return toData(savedAccount);

    }

    private SavingsAccountHold findHoldAccount(Long holdId) {
        return savingsAccountHoldRepository.findById(holdId)
                .orElseThrow(() -> new ResourceNotFoundException("Hold Account not found: " + holdId));
    }

    private SavingsAccount findAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));
    }

    private Client findClient(Long clientId) {
        return clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found: " + clientId));
    }

    private SavingsProduct findProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
    }

    @Transactional
    public SavingsAccountData handleCommand(
            Long accountId,
            String command
    ) {
        SavingsAccount account = accountRepository.findById(accountId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Savings account not found: " + accountId
                        )
                );

        SavingsAccountStatus previousStatus = account.getStatus();
        switch (command.toLowerCase(Locale.ROOT)) {

            case "approve":
                approve(account);
                break;

            case "activate":
                activate(account);
                break;

            case "close":
                throw new IllegalArgumentException("Use Close & settle to close savings accounts");

            case "freeze":
                freeze(account);
                break;

            case "unfreeze":
                unfreeze(account);
                break;

            default:
                throw new IllegalArgumentException(
                        "Unknown command: " + command
                );
        }

        SavingsAccount savedAccount = accountRepository.save(account);
        String event = command.toUpperCase(Locale.ROOT);
        String summary = switch (event) {
            case "FREEZE" -> "FREEZE -> FROZEN";
            case "UNFREEZE" -> "UNFREEZE -> " + savedAccount.getStatus();
            default -> previousStatus + " -> " + savedAccount.getStatus();
        };
        auditTrailService.record(savedAccount, event, summary, null);
        return toData(savedAccount);
    }

    private void approve(SavingsAccount account) {
        if (!SavingsAccountStatus.SUBMITTED.equals(account.getStatus())) {
            throw new IllegalArgumentException(
                    "Account can only be approved from SUBMITTED status"
            );
        }

        account.setStatus(SavingsAccountStatus.APPROVED);
    }

    private void activate(SavingsAccount account) {

        if (!SavingsAccountStatus.APPROVED.equals(account.getStatus())) {
            throw new IllegalArgumentException(
                    "Account can only be activated from APPROVED status"
            );
        }

        account.setStatus(SavingsAccountStatus.ACTIVE);
        account.setActivatedOn(LocalDateTime.now());
    }

    private void freeze(SavingsAccount account) {

        if (account.getFreezeStatus() == FreezeStatus.FROZEN) {
            throw new IllegalArgumentException(
                    "Savings account is already frozen"
            );
        }

        account.setFreezeStatus(FreezeStatus.FROZEN);
        account.setFrozenOn(LocalDateTime.now());
    }

    private void unfreeze(SavingsAccount account) {

        if (account.getFreezeStatus() == FreezeStatus.NOT_FROZEN) {
            throw new IllegalArgumentException(
                    "Savings account is already unfrozen"
            );
        }

        account.setFreezeStatus(FreezeStatus.NOT_FROZEN);
        account.setFrozenOn(LocalDateTime.now());
    }

    private SavingsAccountHoldData toHoldData(SavingsAccountHold accountHold) {
        return new SavingsAccountHoldData(
                accountHold.getId(),
                accountHold.getSavingsAccount().getId(),
                accountHold.getAmount(),
                accountHold.getReason(),
                accountHold.getStatus(),
                accountHold.getCreatedOn(),
                accountHold.getReleasedOn()
        );
    }

    private SavingsAccountData toData(SavingsAccount account) {
        return new SavingsAccountData(
                account.getId(),
                account.getClient().getId(),
                account.getProduct().getId(),
                account.getAccountNo(),
                account.getStatus(),
                account.getBalance(),
                account.getCreatedOn(),
                account.getFreezeStatus(),
                account.getFreezeReason(),
                account.getFrozenOn(),
                account.getActivatedOn()
        );
    }
}