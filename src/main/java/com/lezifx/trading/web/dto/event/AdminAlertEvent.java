package com.lezifx.trading.web.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AdminAlertEvent {
    private String title;
    private String message;
    private String type;
    private Instant timestamp;
    private UUID tenantId;
}