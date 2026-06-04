package com.lezifx.trading.web.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TradingPairToggleResponse {
    private UUID pairId;
    private boolean enabled;
}