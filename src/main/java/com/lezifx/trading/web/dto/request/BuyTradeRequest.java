package com.lezifx.trading.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuyTradeRequest {

    @NotBlank(message = "Pair symbol is required")
    private String pairSymbol;

    @NotNull(message = "Stake amount is required")
    @Positive(message = "Stake amount must be positive")
    private BigDecimal stakeAmount;

    @NotNull(message = "Duration is required")
    private Integer durationSeconds;

    private boolean isDemo;
}