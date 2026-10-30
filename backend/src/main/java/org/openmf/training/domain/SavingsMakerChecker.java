package org.openmf.training.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "m_savings_maker_checker")
@Getter
@Setter
public class SavingsMakerChecker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "savings_account_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_savings_maker_checker_account")
    )
    private SavingsAccount savingsAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "command_type", nullable = false, length = 30)
    private SavingsMakerCheckerCommandType commandType;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "amount", precision = 19, scale = 6)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SavingsMakerCheckerStatus status;

    @Column(name = "made_by", nullable = false, length = 100)
    private String madeBy;

    @Column(name = "made_on", nullable = false)
    private LocalDateTime madeOn;

    @Column(name = "checked_by", length = 100)
    private String checkedBy;

    @Column(name = "checked_on")
    private LocalDateTime checkedOn;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;
}
