package com.lezifx.trading.web.dto.event;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlatformEvent {

    private String     event;
    private String     mode;
    private Boolean    killSwitch;
    private BigDecimal floorBalance;
    private UUID       tenantId;
    private Instant    timestamp;
}