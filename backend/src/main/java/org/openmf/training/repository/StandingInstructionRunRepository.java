package org.openmf.training.repository;

import org.openmf.training.domain.StandingInstructionRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StandingInstructionRunRepository
        extends JpaRepository<StandingInstructionRun, Long> {

    boolean existsByStandingInstruction_IdAndBusinessDate(Long standingInstructionId, LocalDate businessDate);

    List<StandingInstructionRun> findByStandingInstruction_IdOrderByBusinessDateDescRanOnDesc(
            Long standingInstructionId
    );

    Optional<StandingInstructionRun> findByMakerCheckerId(Long makerCheckerId);
}
