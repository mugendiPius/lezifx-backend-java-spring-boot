package com.lezifx.trading.web.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class WithdrawalResponse {
    private UUID withdrawalId;
    private BigDecimal amount;
    private String status;
    private String message;
    private Instant createdAt;
}