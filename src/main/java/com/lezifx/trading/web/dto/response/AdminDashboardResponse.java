package com.lezifx.trading.web.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AdminDashboardResponse {
    // ── User stats ─────────────────────────────────────────────────────
    private long totalUsers;
    private long newSignupsToday;
    private long newSignupsWeek;
    private long activeStakersNow;
    private long activeStakersToday;
    private long activeStakersWeek;

    // ── Financial stats ────────────────────────────────────────────────
    private BigDecimal totalDepositsToday;
    private BigDecimal totalWithdrawsToday;
    private BigDecimal totalStakesLive;
    private BigDecimal totalVolume24h;

    // ── House / platform ───────────────────────────────────────────────
    private BigDecimal houseBalance;
    private BigDecimal floorBalance;
    private String platformMode;
    private boolean killSwitchActive;
    private boolean winModeRejected;

    // ── Extra fields used internally ───────────────────────────────────
    private BigDecimal houseRatio;
    private long activeTrades;
    private BigDecimal totalDeposited;
    private BigDecimal totalWithdrawn;
    private long pendingWithdrawals;
    private BigDecimal grossProfit;
}