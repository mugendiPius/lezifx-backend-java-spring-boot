package com.lezifx.trading.web.controller.superadmin;

import com.lezifx.trading.infrastructure.audit.AuditLogService;
import com.lezifx.trading.service.platform.HouseBalanceService;
import com.lezifx.trading.service.superadmin.TenantService;
import com.lezifx.trading.web.dto.request.AdjustHouseBalanceRequest;
import com.lezifx.trading.web.dto.request.CreateApiKeyRequest;
import com.lezifx.trading.web.dto.request.CreateTenantRequest;
import com.lezifx.trading.web.dto.request.MarketerWithdrawalConfigRequest;
import com.lezifx.trading.web.dto.request.SetDarajaCredentialsRequest;
import com.lezifx.trading.web.dto.request.UpdateTenantDomainsRequest;
import com.lezifx.trading.web.dto.request.UpdateTenantSettingsRequest;
import com.lezifx.trading.web.dto.request.UpdateTenantStatusRequest;
import com.lezifx.trading.web.dto.response.TenantApiKeyResponse;
import com.lezifx.trading.web.dto.response.TenantDetailResponse;
import com.lezifx.trading.web.dto.response.TenantSummaryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/superadmin/tenants")
@RequiredArgsConstructor
public class TenantManagementController {

    private final TenantService      tenantService;
    private final HouseBalanceService houseBalanceService;
    private final com.lezifx.trading.repository.TenantRepository tenantRepository;
    private final AuditLogService    auditLogService;

    @PostMapping
    public ResponseEntity<TenantDetailResponse> createTenant(
            @Valid @RequestBody CreateTenantRequest req,
            @AuthenticationPrincipal String adminId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tenantService.createTenant(req, adminId));
    }

    @GetMapping
    public ResponseEntity<Page<TenantSummaryResponse>> listTenants(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(tenantService.listTenants(status, search, page, size));
    }

    @GetMapping("/{tenantId}")
    public ResponseEntity<TenantDetailResponse> getTenant(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(tenantService.getTenantDetail(tenantId));
    }

    @PutMapping("/{tenantId}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable UUID tenantId,
            @Valid @RequestBody UpdateTenantStatusRequest req,
            @AuthenticationPrincipal String adminId) {
        tenantService.updateTenantStatus(tenantId, req.getStatus(), adminId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{tenantId}/settings")
    public ResponseEntity<Void> updateSettings(
            @PathVariable UUID tenantId,
            @RequestBody UpdateTenantSettingsRequest req,
            @AuthenticationPrincipal String adminId) {
        tenantService.updateTenantSettings(tenantId, req, adminId);
        return ResponseEntity.ok().build();
    }

    /**
     * SUPER_ADMIN domain management  replaces entire allowed_origins for a tenant.
     * Tenant admins use PUT /admin/platform/domains for self-service.
     * SUPER_ADMIN uses this for emergency correction or initial setup.
     */
    @PutMapping("/{tenantId}/domains")
    public ResponseEntity<Map<String, Object>> updateDomains(
            @PathVariable UUID tenantId,
            @RequestBody UpdateTenantDomainsRequest req,
            @AuthenticationPrincipal String adminId) {
        tenantService.updateAllowedOrigins(tenantId, req.getDomains(), adminId);
        return ResponseEntity.ok(Map.of(
                "message", "Domains updated",
                "domains", req.getDomains()
        ));
    }

    @PutMapping("/{tenantId}/daraja")
    public ResponseEntity<Void> setDarajaCredentials(
            @PathVariable UUID tenantId,
            @RequestBody SetDarajaCredentialsRequest req,
            @AuthenticationPrincipal String adminId) {
        tenantService.setDarajaCredentials(tenantId, req, adminId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{tenantId}/api-keys")
    public ResponseEntity<List<TenantApiKeyResponse>> listApiKeys(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(tenantService.listApiKeys(tenantId));
    }

    @PostMapping("/{tenantId}/api-keys")
    public ResponseEntity<TenantApiKeyResponse> createApiKey(
            @PathVariable UUID tenantId,
            @RequestBody CreateApiKeyRequest req,
            @AuthenticationPrincipal String adminId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tenantService.rotateApiKey(tenantId, req.getLabel(), adminId));
    }

    @DeleteMapping("/{tenantId}/api-keys/{keyId}")
    public ResponseEntity<Void> revokeApiKey(
            @PathVariable UUID tenantId,
            @PathVariable UUID keyId,
            @AuthenticationPrincipal String adminId) {
        tenantService.revokeApiKey(tenantId, keyId, adminId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{tenantId}/house-balance/adjust")
    public ResponseEntity<Map<String, BigDecimal>> adjustHouseBalance(
            @PathVariable UUID tenantId,
            @Valid @RequestBody AdjustHouseBalanceRequest req,
            @AuthenticationPrincipal String adminId) {
        houseBalanceService.adjustBalance(tenantId, req.getDelta());
        BigDecimal newBalance = tenantRepository.findById(tenantId)
                .map(t -> t.getHouseBalance()).orElse(BigDecimal.ZERO);
        auditLogService.record(
                TenantService.MASTER_TENANT_ID, adminId, "SUPER_ADMIN",
                "ADJUST_HOUSE_BALANCE", "Tenant", tenantId,
                null,
                Map.of("delta", req.getDelta().toPlainString(), "reason", req.getReason()),
                null);
        return ResponseEntity.ok(Map.of("houseBalance", newBalance));
    }

    @PutMapping("/{tenantId}/marketer-withdrawal")
    public ResponseEntity<Void> configureMarketerWithdrawal(
            @PathVariable UUID tenantId,
            @Valid @RequestBody MarketerWithdrawalConfigRequest req,
            @AuthenticationPrincipal String adminId) {
        tenantService.configureMarketerWithdrawal(tenantId, req, adminId);
        return ResponseEntity.ok().build();
    }
}