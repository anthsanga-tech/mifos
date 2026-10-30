package org.openmf.training.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "m_savings_transaction")
@Getter
@Setter
public class SavingsTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "savings_account_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_savings_transaction_account")
    )
    private SavingsAccount savingsAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private SavingsTransactionType transactionType;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    @Column(name = "running_balance", nullable = false)
    private BigDecimal runningBalance;

    @Column(name = "reversed", nullable = false)
    private Short reversed;

    @Column(name = "transaction_group_id", length = 100)
    private String transactionGroupId;

    @Column(name = "interest_period_from")
    private LocalDate interestPeriodFrom;

    @Column(name = "interest_period_to")
    private LocalDate interestPeriodTo;
}
