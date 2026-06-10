package com.lezifx.trading.web.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class TenantDetailResponse {

    private UUID   id;
    private String brandName;
    private String logoUrl;
    private String faviconUrl;
    private String primaryColor;
    private String accentColor;
    private String supportEmail;

    /** Full list of allowed origins. Replaces customDomain. */
    private List<String> allowedOrigins;

    private String     status;
    private String     platformMode;
    private BigDecimal houseBalance;
    private BigDecimal floorBalance;
    private BigDecimal houseRatio;
    private BigDecimal minDeposit;
    private BigDecimal maxDeposit;
    private BigDecimal minWithdrawal;
    private BigDecimal maxWithdrawal;
    private BigDecimal autoWithdrawalLimit;
    private BigDecimal demoBalance;
    private Integer    maxConcurrentTrades;
    private Boolean    kycRequired;
    private Boolean    registrationOpen;
    private long       userCount;
    private long       activeApiKeys;
    private String     apiKey;
    private Instant    createdAt;
}