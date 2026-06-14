package com.lezifx.trading.web.controller.admin;

import com.lezifx.trading.domain.audit.AdminAuditLog;
import com.lezifx.trading.infrastructure.context.TenantContext;
import com.lezifx.trading.repository.AdminAuditLogRepository;
import com.lezifx.trading.web.dto.response.AuditLogResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AdminAuditLogRepository auditLogRepository;

    /**
     * Paginated audit-log list for the current tenant.
     *
     * Query params:
     *   action  — exact action string (e.g. SET_KILL_SWITCH, UPDATE_BRANDING)
     *   actorId — filter by the admin user who performed the action
     *   from    — ISO date (yyyy-MM-dd), inclusive
     *   to      — ISO date (yyyy-MM-dd), inclusive (end of day)
     *   page    — 0-based (default 0)
     *   size    — page size (default 20, max 100)
     */
    @GetMapping
    public ResponseEntity<Page<AuditLogResponse>> listAuditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID tenantId = TenantContext.get();
        int safeSize  = Math.min(size, 100);

        String actionFilter = (action != null && !action.isBlank()) ? action : null;

        Instant fromInstant = from != null ? from.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        Instant toInstant   = to   != null ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;

        Page<AdminAuditLog> logs = auditLogRepository.findFiltered(
                tenantId, actionFilter, actorId, fromInstant, toInstant,
                PageRequest.of(page, safeSize));

        return ResponseEntity.ok(logs.map(this::toResponse));
    }

    /** Returns the distinct action strings recorded for this tenant — used to populate the filter dropdown. */
    @GetMapping("/actions")
    public ResponseEntity<List<String>> listActions() {
        return ResponseEntity.ok(auditLogRepository.findDistinctActionsByTenant(TenantContext.get()));
    }

    private AuditLogResponse toResponse(AdminAuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .tenantId(log.getTenantId())
                .actorId(log.getActorId())
                .actorRole(log.getActorRole())
                .action(log.getAction())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .oldValue(log.getOldValue())
                .newValue(log.getNewValue())
                .ipAddress(log.getIpAddress())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
