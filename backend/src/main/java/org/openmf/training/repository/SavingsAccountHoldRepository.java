package org.openmf.training.repository;

import org.openmf.training.domain.SavingsAccountHold;
import org.openmf.training.domain.SavingsAccountHoldStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface SavingsAccountHoldRepository
        extends JpaRepository<SavingsAccountHold, Long> {

    List<SavingsAccountHold> findBySavingsAccountIdOrderByCreatedOnDesc(
            Long accountId
    );

    List<SavingsAccountHold> findBySavingsAccountIdAndStatus(
            Long accountId,
            SavingsAccountHoldStatus status
    );

    @Query("""
        SELECT COALESCE(SUM(h.amount), 0)
        FROM SavingsAccountHold h
        WHERE h.savingsAccount.id = :accountId
          AND h.status = :status
    """)
    BigDecimal sumAmountByAccountIdAndStatus(
            @Param("accountId") Long accountId,
            @Param("status") SavingsAccountHoldStatus status
    );

    long countBySavingsAccountIdAndStatus(Long accountId, SavingsAccountHoldStatus status);
}
