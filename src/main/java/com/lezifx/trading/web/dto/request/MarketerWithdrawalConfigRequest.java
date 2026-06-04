package com.lezifx.trading.web.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MarketerWithdrawalConfigRequest {
    @NotNull(message = "enabled is required")
    private Boolean enabled;

    @NotNull(message = "maxWithdrawal is required")
    @DecimalMin(value = "0", message = "maxWithdrawal must be >= 0")
    private BigDecimal maxWithdrawal;
}