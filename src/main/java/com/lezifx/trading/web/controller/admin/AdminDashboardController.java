package com.lezifx.trading.web.controller.admin;

import com.lezifx.trading.infrastructure.audit.AuditLogService;
import com.lezifx.trading.infrastructure.context.TenantContext;
import com.lezifx.trading.repository.TenantRepository;
import com.lezifx.trading.service.admin.AdminAlertService;
import com.lezifx.trading.service.admin.AdminDashboardService;
import com.lezifx.trading.service.admin.CloudinaryService;
import com.lezifx.trading.web.dto.request.BroadcastAlertRequest;
import com.lezifx.trading.web.dto.request.UpdateBrandingUrlRequest;
import com.lezifx.trading.web.dto.response.AdminDashboardResponse;
import com.lezifx.trading.web.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Validated
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;
    private final AdminAlertService adminAlertService;
    private final CloudinaryService cloudinaryService;
    private final TenantRepository tenantRepository;
    private final AuditLogService auditLogService;

    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardResponse> getDashboard() {
        UUID tenantId = TenantContext.get();
        return ResponseEntity.ok(adminDashboardService.getDashboard(tenantId));
    }

    @PostMapping("/alerts")
    public ResponseEntity<Map<String, String>> broadcastAlert(
            @Valid @RequestBody BroadcastAlertRequest request,
            @AuthenticationPrincipal String adminId) {
        UUID tenantId = TenantContext.get();
        adminAlertService.broadcastAlert(tenantId, request.getTitle(),
            request.getMessage(), request.getType(), adminId);
        return ResponseEntity.ok(Map.of("message", "Alert broadcast successfully"));
    }

    /**
     * Step 1 of direct upload: frontend requests a signed upload preset.
     * Returns { signature, timestamp, cloudName, apiKey, folder }.
     * Frontend uses this to POST directly to Cloudinary.
     */
    @GetMapping("/branding/upload-signature")
    public ResponseEntity<Map<String, Object>> getBrandingUploadSignature(
            @RequestParam String type) {
        if (!"logo".equals(type) && !"favicon".equals(type)) {
            throw new BusinessException("INVALID_TYPE", "Type must be 'logo' or 'favicon'");
        }
        UUID tenantId = TenantContext.get();
        String folder = "lezifx/" + tenantId + "/" + type;
        return ResponseEntity.ok(cloudinaryService.generateUploadSignature(folder));
    }

    /**
     * Step 2 of direct upload: frontend sends back the resulting secure_url.
     * Backend saves it to the tenant record.
     */
    @PutMapping("/branding/url")
    public ResponseEntity<Map<String, String>> updateBrandingUrl(
            @Valid @RequestBody UpdateBrandingUrlRequest request,
            @AuthenticationPrincipal String adminId) {
        UUID tenantId = TenantContext.get();

        if (!"logo".equals(request.getType()) && !"favicon".equals(request.getType())) {
            throw new BusinessException("INVALID_TYPE", "Type must be 'logo' or 'favicon'");
        }

        var tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found"));

        if ("logo".equals(request.getType())) {
            tenant.setLogoUrl(request.getUrl());
        } else {
            tenant.setFaviconUrl(request.getUrl());
        }
        tenantRepository.save(tenant);

        auditLogService.record(tenantId, adminId, "ADMIN", "UPDATE_BRANDING_URL",
            "Tenant", tenantId, null,
            Map.of("type", request.getType(), "url", request.getUrl()), null);

        return ResponseEntity.ok(Map.of("url", request.getUrl(), "type", request.getType()));
    }
}