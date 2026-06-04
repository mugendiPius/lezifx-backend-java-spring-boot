package com.lezifx.trading.web.dto.request;

import com.lezifx.trading.domain.enums.KycStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UpdateKycStatusRequest {
    @NotNull(message = "KYC status is required")
    private KycStatus kycStatus;
}