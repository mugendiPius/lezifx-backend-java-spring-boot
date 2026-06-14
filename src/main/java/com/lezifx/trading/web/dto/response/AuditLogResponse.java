package com.lezifx.trading.web.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {
    private UUID    id;
    private UUID    tenantId;
    private UUID    actorId;
    private String  actorRole;
    private String  action;
    private String  entityType;
    private UUID    entityId;
    private Map<String, Object> oldValue;
    private Map<String, Object> newValue;
    private String  ipAddress;
    private Instant createdAt;
}
