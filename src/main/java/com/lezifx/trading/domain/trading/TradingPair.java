package com.lezifx.trading.domain.trading;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trading_pairs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradingPair {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "base_asset", nullable = false, length = 10)
    private String baseAsset;

    @Column(name = "quote_asset", nullable = false, length = 10)
    private String quoteAsset;

    @Column(name = "category", nullable = false, length = 20)
    private String category;

    @Column(name = "is_enabled")
    private Boolean isEnabled;

    @Column(name = "base_price", nullable = false, precision = 20, scale = 6)
    private BigDecimal basePrice;

    @Column(name = "volatility", nullable = false, precision = 8, scale = 6)
    private BigDecimal volatility;

    @Column(name = "min_stake", precision = 10, scale = 2)
    private BigDecimal minStake;

    @Column(name = "max_stake", precision = 10, scale = 2)
    private BigDecimal maxStake;

    @Column(name = "volatility_multiplier", precision = 5, scale = 2)
    private BigDecimal volatilityMultiplier;

    @Column(name = "allowed_durations", columnDefinition = "integer[]")
    private int[] allowedDurations;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}