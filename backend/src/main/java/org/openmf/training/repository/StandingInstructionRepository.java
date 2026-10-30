package org.openmf.training.repository;

import org.openmf.training.domain.StandingInstruction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StandingInstructionRepository
        extends JpaRepository<StandingInstruction, Long> {

    List<StandingInstruction> findByFromAccountIdOrderByIdAsc(Long fromAccountId);

    List<StandingInstruction> findByToAccountIdOrderByIdAsc(Long toAccountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select instruction from StandingInstruction instruction "
            + "where instruction.fromAccount.id = :accountId order by instruction.id")
    List<StandingInstruction> findByFromAccountIdForUpdate(@Param("accountId") Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select instruction from StandingInstruction instruction "
            + "where instruction.toAccount.id = :accountId order by instruction.id")
    List<StandingInstruction> findByToAccountIdForUpdate(@Param("accountId") Long accountId);
}
