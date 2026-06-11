package com.lezifx.trading.web.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UpdatePlatformSettingsRequest {
    private BigDecimal floorBalance;
    private Boolean    killSwitchActive;
    private BigDecimal autoWithdrawalLimit;
    private BigDecimal minDeposit;
    private BigDecimal maxDeposit;
    private BigDecimal minWithdrawal;
    private BigDecimal maxWithdrawal;
    private Integer    maxConcurrentTrades;
    private BigDecimal demoBalance;
    private Boolean    kycRequired;
    private Boolean    registrationOpen;
    // Branding
    private String     brandName;
    private String     primaryColor;
    private String     accentColor;
    private String     tagline;
    private String     logoUrl;
    private String     faviconUrl;
}