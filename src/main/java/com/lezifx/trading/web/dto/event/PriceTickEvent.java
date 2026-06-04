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
public class PriceTickEvent {

    private String symbol;
    private BigDecimal price;
    private Instant timestamp;
    private UUID tenantId;
}