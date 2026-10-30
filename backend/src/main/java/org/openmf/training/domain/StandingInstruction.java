package org.openmf.training.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "m_standing_instruction")
@Getter
@Setter
public class StandingInstruction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "from_account_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_standing_instruction_from_account")
    )
    private SavingsAccount fromAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "to_account_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_standing_instruction_to_account")
    )
    private SavingsAccount toAccount;

    @Column(name = "amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal amount;

    @Column(name = "frequency", nullable = false, length = 30)
    private String frequency;

    @Column(name = "next_due", nullable = false)
    private LocalDate nextDue;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;
}
