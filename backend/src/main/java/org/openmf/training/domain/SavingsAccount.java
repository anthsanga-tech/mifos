package org.openmf.training.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "m_savings_account",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_savings_account_no", columnNames = "account_no")
        }
)
@Getter
@Setter
public class SavingsAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "client_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_savings_account_client")
    )
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "product_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_savings_account_product")
    )
    private SavingsProduct product;

    @Column(name = "account_no", nullable = false, unique = true)
    private String accountNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SavingsAccountStatus status;

    @Column(name = "balance", nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "created_on", nullable = false)
    private LocalDateTime createdOn;

    @Column(name = "activated_on")
    private LocalDateTime activatedOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "freeze_status", nullable = false)
    private FreezeStatus freezeStatus = FreezeStatus.NOT_FROZEN;

    @Column(name = "freeze_reason")
    private String freezeReason;

    @Column(name = "frozen_on")
    private LocalDateTime frozenOn;

    public BigDecimal getAvailableBalance(BigDecimal activeHoldAmount) {
        return getAvailableBalance(balance, activeHoldAmount);
    }

    public BigDecimal getAvailableBalance(BigDecimal ledgerBalance, BigDecimal activeHoldAmount) {
        BigDecimal holds = activeHoldAmount != null ? activeHoldAmount : BigDecimal.ZERO;
        BigDecimal requiredBalance = product.getMinRequiredBalance();
        return ledgerBalance.subtract(holds).subtract(requiredBalance);
    }

    public BigDecimal getAvailableBalanceForWithdrawal(BigDecimal activeHoldAmount) {
        return getAvailableBalance(activeHoldAmount)
                .subtract(product.getWithdrawalFee());
    }
}
