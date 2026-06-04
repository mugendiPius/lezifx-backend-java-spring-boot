package com.lezifx.trading.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BroadcastAlertRequest {
    @NotBlank(message = "Title is required")
    private String title;
    @NotBlank(message = "Message is required")
    private String message;
    @NotBlank(message = "Type is required")
    private String type;
}