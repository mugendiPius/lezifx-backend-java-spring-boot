package com.lezifx.trading.web.dto.request;

import com.lezifx.trading.domain.enums.UserStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UpdateUserStatusRequest {
    @NotNull(message = "Status is required")
    private UserStatus status;
}