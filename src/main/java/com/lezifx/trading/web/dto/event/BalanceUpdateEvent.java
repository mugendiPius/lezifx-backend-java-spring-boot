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
public class BalanceUpdateEvent {

    private UUID userId;
    private BigDecimal newBalance;
    private boolean isDemo;
    private Instant timestamp;
}