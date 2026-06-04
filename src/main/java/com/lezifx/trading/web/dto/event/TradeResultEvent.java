package com.lezifx.trading.web.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeResultEvent {

    private UUID sessionId;
    private String outcome;
    private BigDecimal profitAmount;
    private BigDecimal actualExitPrice;
    private BigDecimal newBalance;
    private Instant settledAt;
}