package com.lezifx.trading.web.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateTenantSettingsRequest {

    private String brandName;
    private String logoUrl;
    private String faviconUrl;
    private String primaryColor;
    private String accentColor;
    private String supportEmail;

    private BigDecimal minDeposit;
    private BigDecimal maxDeposit;
    private BigDecimal minWithdrawal;
    private BigDecimal maxWithdrawal;
    private BigDecimal autoWithdrawalLimit;
    private BigDecimal demoBalance;
    private Integer maxConcurrentTrades;
    private Boolean kycRequired;
    private Boolean registrationOpen;
}