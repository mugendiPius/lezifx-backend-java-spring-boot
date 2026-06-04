package com.lezifx.trading.service.admin;

import com.lezifx.trading.infrastructure.audit.AuditLogService;
import com.lezifx.trading.web.dto.event.AdminAlertEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminAlertService {

    private final SimpMessagingTemplate messagingTemplate;
    private final AuditLogService auditLogService;

    public void broadcastAlert(UUID tenantId, String title, String message,
                                String type, String adminId) {
        AdminAlertEvent event = AdminAlertEvent.builder()
            .title(title)
            .message(message)
            .type(type)
            .timestamp(Instant.now())
            .tenantId(tenantId)
            .build();

        messagingTemplate.convertAndSend("/topic/" + tenantId + "/admin/alerts", event);

        auditLogService.record(tenantId, adminId, "ADMIN", "BROADCAST_ALERT",
            "Tenant", tenantId, null,
            Map.of("title", title, "type", type), null);
    }
}