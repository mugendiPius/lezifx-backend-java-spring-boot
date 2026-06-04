package com.lezifx.trading.web.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AdminDashboardResponse {
    private BigDecimal houseBalance;
    private BigDecimal floorBalance;
    private BigDecimal houseRatio;
    private String platformMode;
    private long activeTrades;
    private long totalUsers;
    private BigDecimal totalDeposited;
    private BigDecimal totalWithdrawn;
    private long pendingWithdrawals;
    private BigDecimal grossProfit;
}