package org.openmf.training.service;

import org.openmf.training.data.SavingsAccountAuditTrailData;
import org.openmf.training.domain.SavingsAccount;
import org.openmf.training.domain.SavingsAccountAuditTrail;
import org.openmf.training.repository.SavingsAccountAuditTrailRepository;
import org.openmf.training.repository.SavingsAccountRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SavingsAccountAuditTrailWritePlatformService {

    private final SavingsAccountAuditTrailRepository auditRepository;
    private final SavingsAccountRepository accountRepository;

    public SavingsAccountAuditTrailWritePlatformService(
            SavingsAccountAuditTrailRepository auditRepository,
            SavingsAccountRepository accountRepository
    ) {
        this.auditRepository = auditRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public List<SavingsAccountAuditTrailData> retrieveByAccount(Long accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new IllegalArgumentException(
                    "Savings account not found: " + accountId
            );
        }
        return auditRepository
                .findBySavingsAccountIdOrderByCreatedOnAscIdAsc(accountId)
                .stream()
                .map(this::toData)
                .toList();
    }

    @Transactional
    public void record(
            SavingsAccount account,
            String event,
            String summary,
            BigDecimal amount
    ) {
        SavingsAccountAuditTrail audit = new SavingsAccountAuditTrail();
        audit.setSavingsAccount(account);
        audit.setEvent(event);
        audit.setSummary(summary);
        audit.setAmount(amount);
        audit.setCreatedBy(currentActor());
        audit.setCreatedOn(LocalDateTime.now());
        auditRepository.save(audit);
    }

    private String currentActor() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            return "system";
        }
        return authentication.getName();
    }

    private SavingsAccountAuditTrailData toData(SavingsAccountAuditTrail audit) {
        return new SavingsAccountAuditTrailData(
                audit.getId(),
                audit.getSavingsAccount().getId(),
                audit.getCreatedOn(),
                audit.getEvent(),
                audit.getSummary(),
                audit.getAmount(),
                audit.getCreatedBy()
        );
    }
}
