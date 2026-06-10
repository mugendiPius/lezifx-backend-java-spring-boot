package com.lezifx.trading.web.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateTenantRequest {

    @NotBlank
    private String brandName;

    private String logoUrl;
    private String faviconUrl;
    private String primaryColor;
    private String accentColor;
    private String supportEmail;

    /**
     * All domains and deployment URLs for this tenant.
     * Replaces the old single customDomain field.
     * Frontend sends comma-split list: ["poa-trade.com", "poa-trade.onrender.com"]
     */
    private List<String> customDomains;

    @NotBlank
    @Email
    private String adminEmail;

    @NotBlank
    @Size(min = 8)
    private String adminPassword;

    private String adminFullName;

    private BigDecimal demoBalance;
    private BigDecimal floorBalance;
    private BigDecimal minDeposit;
    private BigDecimal maxDeposit;
    private BigDecimal minWithdrawal;
    private BigDecimal maxWithdrawal;
    private BigDecimal autoWithdrawalLimit;
}