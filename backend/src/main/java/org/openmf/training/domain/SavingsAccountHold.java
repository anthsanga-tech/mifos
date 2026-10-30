package org.openmf.training.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "m_savings_account_hold")
public class SavingsAccountHold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "savings_account_id",
            nullable = false
    )
    private SavingsAccount savingsAccount;

    @Column(
            name = "amount",
            nullable = false,
            precision = 19,
            scale = 6
    )
    private BigDecimal amount;

    @Column(name = "reason")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 20
    )
    private SavingsAccountHoldStatus status;

    @Column(name = "created_on", nullable = false)
    private LocalDateTime createdOn;

    @Column(name = "released_on")
    private LocalDateTime releasedOn;

}
