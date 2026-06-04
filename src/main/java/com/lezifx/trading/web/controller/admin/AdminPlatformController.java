package com.lezifx.trading.web.controller.admin;

import com.lezifx.trading.infrastructure.context.TenantContext;
import com.lezifx.trading.service.admin.AdminPlatformService;
import com.lezifx.trading.service.platform.PlatformModeService;
import com.lezifx.trading.web.dto.request.MarketerWithdrawalConfigRequest;
import com.lezifx.trading.web.dto.request.SetDarajaCredentialsRequest;
import com.lezifx.trading.web.dto.request.SetPlatformModeRequest;
import com.lezifx.trading.web.dto.request.UpdatePlatformSettingsRequest;
import com.lezifx.trading.web.dto.response.AdminPlatformSettingsResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/platform")
@RequiredArgsConstructor
@Validated
public class AdminPlatformController {

    private final AdminPlatformService adminPlatformService;
    private final PlatformModeService platformModeService;

    @GetMapping("/mode")
    public ResponseEntity<Map<String, String>> getMode() {
        UUID tenantId = TenantContext.get();
        return ResponseEntity.ok(Map.of("mode", platformModeService.getMode(tenantId).name()));
    }

    @PutMapping("/mode")
    public ResponseEntity<Map<String, String>> setMode(
            @Valid @RequestBody SetPlatformModeRequest request,
            @AuthenticationPrincipal String adminId) {
        UUID tenantId = TenantContext.get();
        adminPlatformService.setMode(tenantId, request.getMode(), adminId);
        return ResponseEntity.ok(Map.of("mode", request.getMode().name()));
    }

    @GetMapping("/settings")
    public ResponseEntity<AdminPlatformSettingsResponse> getSettings() {
        UUID tenantId = TenantContext.get();
        return ResponseEntity.ok(adminPlatformService.getSettings(tenantId));
    }

    @PutMapping("/settings")
    public ResponseEntity<Map<String, String>> updateSettings(
            @RequestBody UpdatePlatformSettingsRequest request,
            @AuthenticationPrincipal String adminId) {
        UUID tenantId = TenantContext.get();
        adminPlatformService.updateSettings(tenantId, request, adminId);
        return ResponseEntity.ok(Map.of("message", "Settings updated"));
    }

    @PutMapping("/daraja")
    public ResponseEntity<Map<String, String>> setDarajaCredentials(
            @RequestBody SetDarajaCredentialsRequest request,
            @AuthenticationPrincipal String adminId) {
        UUID tenantId = TenantContext.get();
        adminPlatformService.setDarajaCredentials(tenantId, request, adminId);
        return ResponseEntity.ok(Map.of("message", "Daraja credentials updated"));
    }

    @PutMapping("/marketer-withdrawal")
    public ResponseEntity<AdminPlatformSettingsResponse> configureMarketerWithdrawal(
            @Valid @RequestBody MarketerWithdrawalConfigRequest request,
            @AuthenticationPrincipal String adminId) {
        UUID tenantId = TenantContext.get();
        return ResponseEntity.ok(
            adminPlatformService.configureMarketerWithdrawal(tenantId, request, adminId));
    }
}