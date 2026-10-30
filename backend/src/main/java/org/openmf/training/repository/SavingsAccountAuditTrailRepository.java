package org.openmf.training.repository;

import org.openmf.training.domain.SavingsAccountAuditTrail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SavingsAccountAuditTrailRepository
        extends JpaRepository<SavingsAccountAuditTrail, Long> {

    List<SavingsAccountAuditTrail> findBySavingsAccountIdOrderByCreatedOnAscIdAsc(
            Long savingsAccountId
    );
}
