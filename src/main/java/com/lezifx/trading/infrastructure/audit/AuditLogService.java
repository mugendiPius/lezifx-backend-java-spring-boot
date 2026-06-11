package com.lezifx.trading.infrastructure.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lezifx.trading.domain.audit.AdminAuditLog;
import com.lezifx.trading.repository.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final ObjectMapper objectMapper;

    @Async
    public void record(UUID tenantId, String actorId, String actorRole,
                       String action, String entityType, UUID entityId,
                       Object oldValue, Object newValue, String ipAddress) {
        try {
            Map<String, Object> oldMap = toMap(oldValue);
            Map<String, Object> newMap = toMap(newValue);

            UUID actorUuid = null;
            if (actorId != null && !actorId.isBlank()) {
                try {
                    actorUuid = UUID.fromString(actorId);
                } catch (IllegalArgumentException ignored) {
                }
            }

            AdminAuditLog entry = AdminAuditLog.builder()
                .tenantId(tenantId)
                .actorId(actorUuid)
                .actorRole(actorRole)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .oldValue(oldMap)
                .newValue(newMap)
                .ipAddress(ipAddress)
                .build();

            adminAuditLogRepository.save(entry);

        } catch (Exception e) {
            log.error("Failed to record audit log  tenantId={}, action={}, error={}",
                tenantId, action, e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value) {
        if (value == null) return null;
        if (value instanceof Map) return (Map<String, Object>) value;
        try {
            String json = objectMapper.writeValueAsString(value);
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("Could not serialize audit value to map: {}", e.getMessage());
            return Map.of("value", value.toString());
        }
    }
}