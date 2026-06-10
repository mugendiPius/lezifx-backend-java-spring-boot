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
public class TradingPairResponse {

    private UUID id;
    private String symbol;
    private String name;
    private String baseAsset;
    private String quoteAsset;
    private String category;
    private BigDecimal basePrice;
    private Boolean isEnabled;
    private BigDecimal minStake;
    private BigDecimal maxStake;
    private int[] allowedDurations;
}