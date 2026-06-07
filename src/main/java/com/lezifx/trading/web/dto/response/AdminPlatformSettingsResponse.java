package com.lezifx.trading.web.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPlatformSettingsResponse {

    private String      platformMode;

    /** BUG 2: floorBalance was missing from getSettings() builder — now included. */
    private BigDecimal  floorBalance;

    /** BUG 4: killSwitchActive was missing entirely — now included. */
    private Boolean     killSwitchActive;

    private BigDecimal  autoWithdrawalLimit;
    private BigDecimal  minDeposit;
    private BigDecimal  maxDeposit;
    private BigDecimal  minWithdrawal;
    private BigDecimal  maxWithdrawal;
    private Integer     maxConcurrentTrades;
    private BigDecimal  demoBalance;
    private Boolean     kycRequired;
    private Boolean     registrationOpen;
    private Boolean     marketerWithdrawalEnabled;
    private BigDecimal  marketerMaxWithdrawal;
}