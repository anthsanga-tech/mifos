package org.openmf.training.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "m_savings_account_note")
@Getter
@Setter
public class SavingsAccountNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "savings_account_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_savings_account_note_account")
    )
    private SavingsAccount savingsAccount;

    @Column(name = "note_body", nullable = false, length = 500)
    private String note;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "created_on", nullable = false)
    private LocalDateTime createdOn;
}
