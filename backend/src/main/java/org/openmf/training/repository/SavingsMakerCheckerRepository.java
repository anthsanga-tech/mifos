package org.openmf.training.repository;

import jakarta.persistence.LockModeType;
import org.openmf.training.domain.SavingsMakerChecker;
import org.openmf.training.domain.SavingsMakerCheckerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SavingsMakerCheckerRepository
        extends JpaRepository<SavingsMakerChecker, Long> {

    List<SavingsMakerChecker> findBySavingsAccountIdOrderByMadeOnDescIdDesc(
            Long savingsAccountId
    );

    List<SavingsMakerChecker> findAllByOrderByMadeOnDescIdDesc();

    List<SavingsMakerChecker> findByStatusOrderByMadeOnDescIdDesc(
            SavingsMakerCheckerStatus status
    );

    List<SavingsMakerChecker> findBySavingsAccountIdAndStatusOrderByMadeOnDescIdDesc(
            Long savingsAccountId,
            SavingsMakerCheckerStatus status
    );

    long countBySavingsAccountIdAndStatus(Long savingsAccountId, SavingsMakerCheckerStatus status);

    long countBySavingsAccountIdAndStatusAndIdNot(
            Long savingsAccountId,
            SavingsMakerCheckerStatus status,
            Long excludedId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from SavingsMakerChecker request where request.id = :id")
    Optional<SavingsMakerChecker> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from SavingsMakerChecker request "
            + "where request.id = :id and request.savingsAccount.id = :accountId")
    Optional<SavingsMakerChecker> findByIdAndAccountIdForUpdate(
            @Param("id") Long id,
            @Param("accountId") Long accountId
    );
}
