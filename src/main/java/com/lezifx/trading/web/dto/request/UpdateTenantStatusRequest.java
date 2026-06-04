package com.lezifx.trading.web.dto.request;

import com.lezifx.trading.domain.enums.TenantStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateTenantStatusRequest {

    @NotNull
    private TenantStatus status;
}