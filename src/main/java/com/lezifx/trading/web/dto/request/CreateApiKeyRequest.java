package com.lezifx.trading.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateApiKeyRequest {

    @NotBlank
    private String label;
}