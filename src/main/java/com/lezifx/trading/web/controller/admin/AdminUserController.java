package com.lezifx.trading.web.controller.admin;

import com.lezifx.trading.infrastructure.context.TenantContext;
import com.lezifx.trading.service.admin.AdminUserService;
import com.lezifx.trading.web.dto.request.UpdateKycStatusRequest;
import com.lezifx.trading.web.dto.request.UpdateUserStatusRequest;
import com.lezifx.trading.web.dto.response.AdminUserDetailResponse;
import com.lezifx.trading.web.dto.response.AdminUserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping("/users")
    public ResponseEntity<Page<AdminUserResponse>> listUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID tenantId = TenantContext.get();
        return ResponseEntity.ok(
            adminUserService.listUsers(tenantId, role, status, search, page, size));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<AdminUserDetailResponse> getUserDetail(
            @PathVariable UUID userId) {
        UUID tenantId = TenantContext.get();
        return ResponseEntity.ok(adminUserService.getUserDetail(userId, tenantId));
    }

    @PutMapping("/users/{userId}/status")
    public ResponseEntity<Map<String, String>> updateUserStatus(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserStatusRequest request,
            @AuthenticationPrincipal String adminId) {
        UUID tenantId = TenantContext.get();
        adminUserService.updateUserStatus(userId, tenantId, request.getStatus(), adminId);
        return ResponseEntity.ok(Map.of("message", "User status updated"));
    }

    @PutMapping("/users/{userId}/kyc-status")
    public ResponseEntity<Map<String, String>> updateKycStatus(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateKycStatusRequest request,
            @AuthenticationPrincipal String adminId) {
        UUID tenantId = TenantContext.get();
        adminUserService.updateKycStatus(userId, tenantId, request.getKycStatus(), adminId);
        return ResponseEntity.ok(Map.of("message", "KYC status updated"));
    }

    @PutMapping("/users/{userId}/reset-demo")
    public ResponseEntity<Map<String, String>> resetDemo(
            @PathVariable UUID userId,
            @AuthenticationPrincipal String adminId) {
        UUID tenantId = TenantContext.get();
        adminUserService.resetDemoBalance(userId, tenantId, adminId);
        return ResponseEntity.ok(Map.of("message", "Demo balance reset"));
    }
}