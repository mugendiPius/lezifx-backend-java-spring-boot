package com.lezifx.trading.web.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class TenantSummaryResponse {

    private UUID id;
    private String brandName;
    private String logoUrl;
    private String customDomain;
    private String status;
    private String platformMode;
    private BigDecimal houseBalance;
    private BigDecimal floorBalance;
    private long userCount;
    private Instant createdAt;
}