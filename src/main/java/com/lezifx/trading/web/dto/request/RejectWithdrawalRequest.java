package com.lezifx.trading.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RejectWithdrawalRequest {
    @NotBlank(message = "Reason is required")
    private String reason;
}