package com.lezifx.trading.web.controller.admin;

import com.lezifx.trading.infrastructure.audit.AuditLogService;
import com.lezifx.trading.infrastructure.context.TenantContext;
import com.lezifx.trading.repository.TenantRepository;
import com.lezifx.trading.web.dto.request.UpdateTenantDomainsRequest;
import com.lezifx.trading.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tenant self-management settings.
 * Tenant ADMINs use these endpoints to manage their own tenant's configuration
 * that does not fit AdminPlatformController (which handles mode, kill-switch, Daraja etc).
 *
 * Currently exposes:
 *   GET  /admin/platform/domains  → returns the tenant's allowed_origins list
 *   PUT  /admin/platform/domains  → replaces the tenant's allowed_origins list
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/platform")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final TenantRepository tenantRepository;
    private final AuditLogService  auditLogService;

    /**
     * Returns the list of allowed origins (domains + deployment URLs) for
     * the current tenant. Used by the frontend Settings → Domains tab.
     */
    @GetMapping("/domains")
    public ResponseEntity<List<String>> getDomains() {
        UUID tenantId = TenantContext.get();
        var tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found"));

        String[] origins = tenant.getAllowedOrigins();
        List<String> domains = (origins != null) ? Arrays.asList(origins) : Collections.emptyList();
        return ResponseEntity.ok(domains);
    }

    /**
     * Replaces the entire allowed_origins array for the current tenant.
     * The frontend sends the full updated list — not a diff.
     * Each domain is normalised (lowercase, protocol stripped).
     */
    @PutMapping("/domains")
    public ResponseEntity<Map<String, Object>> updateDomains(
            @RequestBody UpdateTenantDomainsRequest req,
            @AuthenticationPrincipal String adminId) {

        UUID tenantId = TenantContext.get();
        var tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found"));

        if (req.getDomains() == null) {
            throw new BusinessException("MISSING_FIELD", "domains list is required");
        }

        // Normalise: lowercase, strip protocol, strip trailing slashes
        String[] origins = req.getDomains().stream()
                .map(d -> d.toLowerCase().replaceAll("^https?://", "").replaceAll("/+$", ""))
                .filter(d -> !d.isBlank())
                .toArray(String[]::new);

        tenant.setAllowedOrigins(origins);
        tenantRepository.save(tenant);

        auditLogService.record(tenantId, adminId, "ADMIN",
                "UPDATE_ALLOWED_ORIGINS", "Tenant", tenantId,
                null, Map.of("domains", String.join(", ", origins)), null);

        log.info("Tenant {} updated allowed_origins: {}", tenantId, String.join(", ", origins));

        return ResponseEntity.ok(Map.of(
                "message", "Domains updated successfully",
                "domains", Arrays.asList(origins)
        ));
    }
}