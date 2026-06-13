package com.lezifx.trading.web.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AdminUserResponse {
    private UUID id;
    private UUID tenantId;
    private String email;
    private String fullName;
    private String phoneNumber;
    private String role;
    private String status;
    private String kycStatus;
    @JsonProperty("isMarketer")
    private boolean isMarketer;
    private BigDecimal liveBalance;
    private BigDecimal demoBalance;
    private Instant createdAt;
    private Instant lastLoginAt;
}