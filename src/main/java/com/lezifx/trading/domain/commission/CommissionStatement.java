package com.lezifx.trading.domain.commission;

import com.lezifx.trading.domain.enums.StatementStatus;
import com.lezifx.trading.domain.tenant.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "commission_statements")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommissionStatement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "total_trades", nullable = false)
    private Integer totalTrades;

    @Column(name = "total_staked", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalStaked;

    @Column(name = "total_paid_out", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalPaidOut;

    @Column(name = "gross_profit", nullable = false, precision = 15, scale = 2)
    private BigDecimal grossProfit;

    @Column(name = "platform_commission", nullable = false, precision = 15, scale = 2)
    private BigDecimal platformCommission;

    @Column(name = "tenant_net", nullable = false, precision = 15, scale = 2)
    private BigDecimal tenantNet;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatementStatus status;

    @CreationTimestamp
    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;
}