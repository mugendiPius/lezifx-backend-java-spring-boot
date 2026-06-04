package com.lezifx.trading.web.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class TenantApiKeyResponse {

    private UUID id;
    private String label;
    private String maskedKey;
    private boolean isActive;
    private Instant createdAt;
    private Instant revokedAt;
}