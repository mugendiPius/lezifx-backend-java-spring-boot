package com.lezifx.trading.web.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AdminWithdrawalResponse {
    private UUID id;
    private UUID userId;
    private String userEmail;
    private String userFullName;
    private BigDecimal amount;
    private String status;
    private String phoneNumber;
    private String failureReason;
    private Instant createdAt;
    private Instant approvedAt;
    private Instant completedAt;
}