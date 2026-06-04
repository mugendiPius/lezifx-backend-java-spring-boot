package com.lezifx.trading.web.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateTenantRequest {

    @NotBlank
    private String brandName;

    private String logoUrl;
    private String faviconUrl;
    private String primaryColor;
    private String accentColor;
    private String supportEmail;
    private String customDomain;

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