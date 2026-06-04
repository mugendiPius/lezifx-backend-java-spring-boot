package com.lezifx.trading.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AdjustHouseBalanceRequest {

    @NotNull
    private BigDecimal delta;

    @NotBlank
    private String reason;
}