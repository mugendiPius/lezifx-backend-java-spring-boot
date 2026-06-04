package com.lezifx.trading.web.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SuperAdminStatsResponse {

    private long totalTenants;
    private long activeTenants;
    private long totalUsers;
    private long totalRealTrades;
    private BigDecimal totalGrossProfit;
    private long pendingCommissions;
    private BigDecimal totalCommissionEarned;
}