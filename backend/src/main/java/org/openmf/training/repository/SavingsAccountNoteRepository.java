package org.openmf.training.repository;

import org.openmf.training.domain.SavingsAccountNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SavingsAccountNoteRepository
        extends JpaRepository<SavingsAccountNote, Long> {

    List<SavingsAccountNote> findBySavingsAccountIdOrderByCreatedOnDescIdDesc(
            Long savingsAccountId
    );
}
