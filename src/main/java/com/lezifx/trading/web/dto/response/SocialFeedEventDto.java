package com.lezifx.trading.web.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class SocialFeedEventDto {

    private UUID id;
    private String eventType;
    private String displayName;
    private String action;
    private BigDecimal amount;
    private String pairSymbol;
    @JsonProperty("isSimulated")
    private boolean isSimulated;
    private Instant createdAt;
}