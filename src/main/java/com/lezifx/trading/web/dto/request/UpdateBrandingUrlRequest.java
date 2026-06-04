package com.lezifx.trading.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UpdateBrandingUrlRequest {
    @NotBlank(message = "Type is required")
    private String type;
    @NotBlank(message = "URL is required")
    private String url;
}