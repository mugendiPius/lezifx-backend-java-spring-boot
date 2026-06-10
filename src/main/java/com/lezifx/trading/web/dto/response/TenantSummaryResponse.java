package com.lezifx.trading.web.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class TenantSummaryResponse {

    private UUID   id;
    private String brandName;
    private String logoUrl;

    /** All registered domains/URLs for this tenant. Replaces customDomain. */
    private List<String> allowedOrigins;

    private String     status;
    private String     platformMode;
    private BigDecimal houseBalance;
    private BigDecimal floorBalance;
    private long       userCount;
    private Instant    createdAt;
}