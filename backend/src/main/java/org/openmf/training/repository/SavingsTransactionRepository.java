package org.openmf.training.repository;

import org.openmf.training.domain.SavingsTransaction;
import org.openmf.training.domain.SavingsTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SavingsTransactionRepository
        extends JpaRepository<SavingsTransaction, Long> {

    List<SavingsTransaction> findBySavingsAccountIdOrderByTransactionDateDescIdDesc(
            Long savingsAccountId
    );

    List<SavingsTransaction> findByTransactionGroupId(
            String transactionGroupId
    );

    List<SavingsTransaction> findBySavingsAccountIdOrderByTransactionDateAscIdAsc(
            Long savingsAccountId
    );

    boolean existsBySavingsAccountIdAndTransactionTypeAndReversedAndInterestPeriodFromLessThanEqualAndInterestPeriodToGreaterThanEqual(
            Long savingsAccountId,
            SavingsTransactionType transactionType,
            Short reversed,
            LocalDate periodTo,
            LocalDate periodFrom
    );
}