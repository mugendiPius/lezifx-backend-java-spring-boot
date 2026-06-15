package com.lezifx.trading.domain.trading;

import com.lezifx.trading.domain.enums.TradeOutcome;
import com.lezifx.trading.domain.enums.TradeSessionStatus;
import com.lezifx.trading.domain.tenant.Tenant;
import com.lezifx.trading.domain.user.User;
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
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trade_sessions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "pair_symbol", nullable = false, length = 20)
    private String pairSymbol;

    @Column(name = "is_demo", nullable = false)
    private Boolean isDemo;

    @Column(name = "is_marketer_trade", nullable = false)
    private Boolean isMarketerTrade;

    @Column(name = "stake_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal stakeAmount;

    @Column(name = "entry_price", nullable = false, precision = 20, scale = 6)
    private BigDecimal entryPrice;

    @Column(name = "sealed_exit_price", nullable = false, precision = 20, scale = 6)
    private BigDecimal sealedExitPrice;

    @Column(name = "locked_payout_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal lockedPayoutRate;

    @Column(name = "duration_seconds", nullable = false)
    private Integer durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TradeSessionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 10)
    private TradeOutcome outcome;

    @Column(name = "profit_amount", precision = 15, scale = 2)
    private BigDecimal profitAmount;

    @Column(name = "actual_exit_price", precision = 20, scale = 6)
    private BigDecimal actualExitPrice;

    @CreationTimestamp
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "path_type", length = 20)
    private String pathType;

    @Column(name = "pivot_price", precision = 20, scale = 6)
    private BigDecimal pivotPrice;

    // FIX B2: track last mutation time so orphaned SETTLING rows can be detected
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}