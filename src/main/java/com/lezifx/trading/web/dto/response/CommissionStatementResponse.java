package com.lezifx.trading.web.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CommissionStatementResponse {

    private UUID id;
    private UUID tenantId;
    private String tenantBrandName;
    private Instant periodStart;
    private Instant periodEnd;
    private BigDecimal grossProfit;
    private BigDecimal commissionRate;
    private BigDecimal commissionAmount;
    private String status;
    private Instant createdAt;
}