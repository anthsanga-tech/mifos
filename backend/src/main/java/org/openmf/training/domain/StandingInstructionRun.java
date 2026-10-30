package org.openmf.training.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "m_savings_standing_instruction_run")
@Getter
@Setter
public class StandingInstructionRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "standing_instruction_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_si_run_standing_instruction")
    )
    private StandingInstruction standingInstruction;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "transfer_group_id", length = 100)
    private String transferGroupId;

    @Column(name = "maker_checker_id")
    private Long makerCheckerId;

    @Column(name = "ran_on", nullable = false)
    private LocalDateTime ranOn;
}
