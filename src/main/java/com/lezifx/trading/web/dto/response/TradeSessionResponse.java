package com.lezifx.trading.web.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeSessionResponse {

    private UUID id;
    private UUID tenantId;
    private UUID userId;
    private String pairSymbol;
    @JsonProperty("isDemo")
    private boolean isDemo;

    @JsonProperty("isMarketerTrade")
    private boolean isMarketerTrade;
    private BigDecimal stakeAmount;
    private BigDecimal entryPrice;
    private BigDecimal lockedPayoutRate;
    private int durationSeconds;
    private String status;
    private Instant startedAt;
    private Instant expiresAt;

    // Nullable — only present after settlement
    private String outcome;
    private BigDecimal profitAmount;
    private BigDecimal actualExitPrice;
    private Instant settledAt;
}