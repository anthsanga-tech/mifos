package org.openmf.training.service;

import org.openmf.training.data.CreateSavingsAccountNoteRequest;
import org.openmf.training.data.SavingsAccountNoteData;
import org.openmf.training.domain.SavingsAccount;
import org.openmf.training.domain.SavingsAccountNote;
import org.openmf.training.repository.SavingsAccountNoteRepository;
import org.openmf.training.repository.SavingsAccountRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SavingsAccountNoteWritePlatformService {

    private static final int MAX_NOTE_LENGTH = 500;

    private final SavingsAccountNoteRepository noteRepository;
    private final SavingsAccountRepository accountRepository;

    public SavingsAccountNoteWritePlatformService(
            SavingsAccountNoteRepository noteRepository,
            SavingsAccountRepository accountRepository
    ) {
        this.noteRepository = noteRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public List<SavingsAccountNoteData> retrieveByAccount(Long accountId) {
        requireAccount(accountId);
        return noteRepository
                .findBySavingsAccountIdOrderByCreatedOnDescIdDesc(accountId)
                .stream()
                .map(this::toData)
                .toList();
    }

    @Transactional
    public SavingsAccountNoteData create(
            Long accountId,
            CreateSavingsAccountNoteRequest request
    ) {
        if (request == null || request.note() == null || request.note().isBlank()) {
            throw new IllegalArgumentException("Note body is required");
        }
        String noteBody = request.note().trim();
        if (noteBody.length() > MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException(
                    "Note body must not exceed " + MAX_NOTE_LENGTH + " characters"
            );
        }

        SavingsAccountNote note = new SavingsAccountNote();
        note.setSavingsAccount(requireAccount(accountId));
        note.setNote(noteBody);
        note.setCreatedBy(currentActor());
        note.setCreatedOn(LocalDateTime.now());
        return toData(noteRepository.save(note));
    }

    @Transactional
    public void delete(Long accountId, Long noteId) {
        requireAccount(accountId);
        SavingsAccountNote note = noteRepository.findById(noteId)
                .orElseThrow(() -> new IllegalArgumentException("Note not found: " + noteId));
        if (!note.getSavingsAccount().getId().equals(accountId)) {
            throw new IllegalArgumentException(
                    "Note does not belong to this savings account"
            );
        }
        noteRepository.delete(note);
    }

    private SavingsAccount requireAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Savings account not found: " + accountId
                ));
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

    private SavingsAccountNoteData toData(SavingsAccountNote note) {
        return new SavingsAccountNoteData(
                note.getId(),
                note.getSavingsAccount().getId(),
                note.getNote(),
                note.getCreatedBy(),
                note.getCreatedOn()
        );
    }
}
