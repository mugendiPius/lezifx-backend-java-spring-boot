package com.lezifx.trading.web.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class DepositStatusResponse {
    private UUID depositId;
    private String status;
    private BigDecimal amount;
    private String mpesaReceiptNumber;
    private String failureReason;
    private Instant createdAt;
    private Instant completedAt;
}
