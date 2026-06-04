package com.lezifx.trading.web.dto.request;

import com.lezifx.trading.domain.enums.PlatformMode;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SetPlatformModeRequest {
    @NotNull(message = "Mode is required")
    private PlatformMode mode;
}