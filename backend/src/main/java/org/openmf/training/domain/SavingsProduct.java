package org.openmf.training.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "m_savings_product")
@Getter
@Setter
public class SavingsProduct {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "nominal_annual_interest_rate", nullable = false)
    private BigDecimal nominalAnnualInterestRate;

    @Column(name = "created_on", nullable = false)
    private LocalDateTime createdOn;


    @Column(name = "min_required_balance", nullable = false)
    private BigDecimal minRequiredBalance;

    @Column(name = "withdrawal_fee", nullable = false)
    private BigDecimal withdrawalFee;

    @Column(name = "closure_fee", nullable = false)
    private BigDecimal closureFee;
}