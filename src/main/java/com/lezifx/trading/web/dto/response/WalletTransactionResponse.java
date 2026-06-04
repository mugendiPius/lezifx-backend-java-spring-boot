package com.lezifx.trading.web.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class WalletTransactionResponse {
    private UUID id;
    private String type;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private boolean isDemo;
    private boolean isMarketerTransaction;
    private UUID referenceId;
    private String referenceType;
    private String description;
    private Instant createdAt;
}