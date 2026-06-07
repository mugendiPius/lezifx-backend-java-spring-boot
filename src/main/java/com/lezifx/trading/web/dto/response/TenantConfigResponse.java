package com.lezifx.trading.web.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantConfigResponse {

    private UUID       tenantId;
    private String     brandName;
    private String     logoUrl;
    private String     faviconUrl;
    private String     primaryColor;
    private String     accentColor;
    private String     supportEmail;
    private String     apiKey;
    private String     tenantType;
    private FeaturesDto features;

    // BUG 2 FIX: added so guests can poll kill-switch + mode without WS
    private boolean    killSwitchActive;
    private String     platformMode;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeaturesDto {
        private boolean    registrationOpen;
        private boolean    kycRequired;
        private boolean    demoEnabled;
        private BigDecimal defaultDemoBalance;
        private BigDecimal minDeposit;
        private BigDecimal maxDeposit;
        private BigDecimal minWithdrawal;
        private BigDecimal maxWithdrawal;
        private BigDecimal autoWithdrawalLimit;
        private int        maxConcurrentTrades;
    }
}