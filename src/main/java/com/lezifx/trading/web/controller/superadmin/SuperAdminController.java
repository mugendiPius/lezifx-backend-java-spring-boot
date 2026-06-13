package com.lezifx.trading.web.controller.superadmin;

import com.lezifx.trading.domain.enums.KycStatus;
import com.lezifx.trading.domain.enums.PlatformMode;
import com.lezifx.trading.domain.enums.TenantStatus;
import com.lezifx.trading.domain.enums.UserRole;
import com.lezifx.trading.domain.enums.UserStatus;
import com.lezifx.trading.domain.user.User;
import com.lezifx.trading.domain.wallet.Wallet;
import com.lezifx.trading.infrastructure.audit.AuditLogService;
import com.lezifx.trading.repository.TenantRepository;
import com.lezifx.trading.repository.UserRepository;
import com.lezifx.trading.repository.WalletRepository;
import com.lezifx.trading.service.admin.AdminDashboardService;
import com.lezifx.trading.service.admin.AdminPlatformService;
import com.lezifx.trading.service.superadmin.TenantService;
import com.lezifx.trading.web.dto.request.ManageTenantAdminRequest;
import com.lezifx.trading.web.dto.request.UpdatePlatformSettingsRequest;
import com.lezifx.trading.web.dto.request.UpdateTenantDomainsRequest;
import com.lezifx.trading.web.dto.response.AdminDashboardResponse;
import com.lezifx.trading.web.dto.response.TenantAdminResponse;
import com.lezifx.trading.web.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * SuperAdmin cross-tenant operations that do not fit TenantManagementController:
 *
 *  GET  /superadmin/tenants/{id}/dashboard   live snapshot of a tenant's metrics
 *  GET  /superadmin/tenants/{id}/admins      list ADMIN users for a tenant
 *  POST /superadmin/tenants/{id}/admins      create / elevate / reset password
 *  PUT  /superadmin/tenants/{id}/domains     replace allowed_origins for a tenant
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/superadmin")
@RequiredArgsConstructor
public class SuperAdminController {

    private static final UUID MASTER_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final AdminDashboardService adminDashboardService;
    private final AdminPlatformService  adminPlatformService;
    private final TenantService         tenantService;
    private final UserRepository        userRepository;
    private final WalletRepository      walletRepository;
    private final TenantRepository      tenantRepository;
    private final AuditLogService       auditLogService;
    private final PasswordEncoder       passwordEncoder;

    //  Tenant dashboard snapshot 

    /**
     * Returns the same AdminDashboardResponse as the ADMIN dashboard endpoint,
     * but for any tenant. SUPER_ADMIN uses this to monitor tenant health
     * without needing to log in as that tenant's admin.
     */
    @GetMapping("/tenants/{tenantId}/dashboard")
    public ResponseEntity<AdminDashboardResponse> getTenantDashboard(
            @PathVariable UUID tenantId) {
        // Verify tenant exists (throws if not)
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found"));
        return ResponseEntity.ok(adminDashboardService.getDashboard(tenantId));
    }

    //  Tenant admin list 

    /**
     * Lists all users with role ADMIN in the given tenant.
     * SUPER_ADMIN uses this to see who currently manages a tenant.
     */
    @GetMapping("/tenants/{tenantId}/admins")
    public ResponseEntity<List<TenantAdminResponse>> listTenantAdmins(
            @PathVariable UUID tenantId) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found"));

        List<TenantAdminResponse> admins = userRepository
                .findByTenantIdAndRole(tenantId, UserRole.ADMIN)
                .stream()
                .map(u -> TenantAdminResponse.builder()
                        .id(u.getId())
                        .email(u.getEmail())
                        .fullName(u.getFullName())
                        .phoneNumber(u.getPhoneNumber())
                        .role(u.getRole().name())
                        .status(u.getStatus().name())
                        .createdAt(u.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(admins);
    }

    //  Admin management 

    /**
     * Three actions in one endpoint, gated by the `action` field:
     *
     * CREATE          creates a brand new ADMIN user in the tenant.
     * ELEVATE         promotes an existing PLAYER/MARKETER user to ADMIN.
     * RESET_PASSWORD  changes an existing user's password (any role).
     *
     * SUPER_ADMIN cannot change operational data (trades, withdrawals, balances).
     * These are purely user management actions.
     */
    @PostMapping("/tenants/{tenantId}/admins")
    public ResponseEntity<TenantAdminResponse> manageTenantAdmin(
            @PathVariable UUID tenantId,
            @RequestBody ManageTenantAdminRequest req,
            @AuthenticationPrincipal String superAdminId) {

        var tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found"));

        String action = req.getAction();
        if (action == null) {
            throw new BusinessException("MISSING_ACTION", "action field is required: CREATE | ELEVATE | RESET_PASSWORD");
        }

        return switch (action.toUpperCase()) {
            case "CREATE" -> {
                // Validate required fields
                if (req.getEmail() == null || req.getEmail().isBlank())
                    throw new BusinessException("MISSING_FIELD", "email is required");
                if (req.getPassword() == null || req.getPassword().length() < 8)
                    throw new BusinessException("WEAK_PASSWORD", "password must be at least 8 characters");
                if (req.getFullName() == null || req.getFullName().isBlank())
                    throw new BusinessException("MISSING_FIELD", "fullName is required");

                // Email must be unique within the tenant
                if (userRepository.findByTenantIdAndEmail(tenantId, req.getEmail()).isPresent())
                    throw new BusinessException("EMAIL_TAKEN", "Email already registered in this tenant");

                User newAdmin = User.builder()
                        .tenant(tenant)
                        .email(req.getEmail())
                        .passwordHash(passwordEncoder.encode(req.getPassword()))
                        .fullName(req.getFullName())
                        .phoneNumber(req.getPhoneNumber())
                        .role(UserRole.ADMIN)
                        .status(UserStatus.ACTIVE)
                        .isMarketer(false)
                        .marketerBalance(BigDecimal.ZERO)
                        .kycStatus(KycStatus.NONE)
                        .build();

                newAdmin = userRepository.save(newAdmin);

                // Create wallet for the new admin
                BigDecimal demoBalance = tenant.getDemoBalance() != null
                        ? tenant.getDemoBalance() : BigDecimal.valueOf(10_000);
                walletRepository.save(Wallet.builder()
                        .user(newAdmin)
                        .tenant(tenant)
                        .liveBalance(BigDecimal.ZERO)
                        .demoBalance(demoBalance)
                        .version(0L)
                        .build());

                auditLogService.record(MASTER_TENANT_ID, superAdminId, "SUPER_ADMIN",
                        "CREATE_TENANT_ADMIN", "User", newAdmin.getId(),
                        null, Map.of("tenantId", tenantId.toString(), "email", req.getEmail()), null);

                log.info("SUPER_ADMIN created admin {} for tenant {}", req.getEmail(), tenantId);

                yield ResponseEntity.status(HttpStatus.CREATED).body(
                        TenantAdminResponse.builder()
                                .id(newAdmin.getId())
                                .email(newAdmin.getEmail())
                                .fullName(newAdmin.getFullName())
                                .phoneNumber(newAdmin.getPhoneNumber())
                                .role(newAdmin.getRole().name())
                                .status(newAdmin.getStatus().name())
                                .createdAt(newAdmin.getCreatedAt())
                                .build()
                );
            }

            case "ELEVATE" -> {
                if (req.getEmail() == null || req.getEmail().isBlank())
                    throw new BusinessException("MISSING_FIELD", "email is required to identify the user to elevate");

                User user = userRepository.findByTenantIdAndEmail(tenantId, req.getEmail())
                        .orElseThrow(() -> new BusinessException("USER_NOT_FOUND",
                                "No user with email '" + req.getEmail() + "' found in this tenant"));

                if (user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.SUPER_ADMIN)
                    throw new BusinessException("ALREADY_ADMIN", "User already has admin or higher role");

                UserRole oldRole = user.getRole();
                user.setRole(UserRole.ADMIN);
                userRepository.save(user);

                auditLogService.record(MASTER_TENANT_ID, superAdminId, "SUPER_ADMIN",
                        "ELEVATE_TO_ADMIN", "User", user.getId(),
                        Map.of("role", oldRole.name()),
                        Map.of("role", "ADMIN", "tenantId", tenantId.toString()), null);

                log.info("SUPER_ADMIN elevated user {} to ADMIN in tenant {}", req.getEmail(), tenantId);

                yield ResponseEntity.ok(TenantAdminResponse.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .phoneNumber(user.getPhoneNumber())
                        .role(user.getRole().name())
                        .status(user.getStatus().name())
                        .createdAt(user.getCreatedAt())
                        .build());
            }

            case "RESET_PASSWORD" -> {
                if (req.getEmail() == null || req.getEmail().isBlank())
                    throw new BusinessException("MISSING_FIELD", "email is required");
                if (req.getNewPassword() == null || req.getNewPassword().length() < 8)
                    throw new BusinessException("WEAK_PASSWORD", "newPassword must be at least 8 characters");

                User user = userRepository.findByTenantIdAndEmail(tenantId, req.getEmail())
                        .orElseThrow(() -> new BusinessException("USER_NOT_FOUND",
                                "No user with email '" + req.getEmail() + "' found in this tenant"));

                user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
                userRepository.save(user);

                auditLogService.record(MASTER_TENANT_ID, superAdminId, "SUPER_ADMIN",
                        "RESET_ADMIN_PASSWORD", "User", user.getId(),
                        null, Map.of("tenantId", tenantId.toString(), "email", req.getEmail()), null);

                log.info("SUPER_ADMIN reset password for user {} in tenant {}", req.getEmail(), tenantId);

                yield ResponseEntity.ok(TenantAdminResponse.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .phoneNumber(user.getPhoneNumber())
                        .role(user.getRole().name())
                        .status(user.getStatus().name())
                        .createdAt(user.getCreatedAt())
                        .build());
            }

            default -> throw new BusinessException("INVALID_ACTION",
                    "action must be one of: CREATE, ELEVATE, RESET_PASSWORD");
        };
    }

    //  Emergency per-tenant platform controls

    /**
     * Sets platform mode (WIN / NORMAL / LOSE) for any specific tenant.
     * SUPER_ADMIN uses this for emergency intervention without impersonating the admin.
     */
    @PutMapping("/tenants/{tenantId}/platform/mode")
    public ResponseEntity<Map<String, Object>> setTenantMode(
            @PathVariable UUID tenantId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal String superAdminId) {

        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found"));

        String modeStr = body.get("mode");
        PlatformMode mode;
        try {
            mode = PlatformMode.valueOf(modeStr);
        } catch (Exception e) {
            throw new BusinessException("INVALID_MODE", "mode must be WIN, NORMAL, or LOSE");
        }

        adminPlatformService.setMode(tenantId, mode, superAdminId);
        log.info("SUPER_ADMIN {} set mode={} for tenant {}", superAdminId, mode, tenantId);

        return ResponseEntity.ok(Map.of("tenantId", tenantId, "mode", mode.name()));
    }

    /**
     * Activates or deactivates the kill switch for a specific tenant.
     * SUPER_ADMIN uses this to take a single tenant offline without affecting others.
     */
    @PutMapping("/tenants/{tenantId}/platform/kill-switch")
    public ResponseEntity<Map<String, Object>> setTenantKillSwitch(
            @PathVariable UUID tenantId,
            @RequestBody Map<String, Boolean> body,
            @AuthenticationPrincipal String superAdminId) {

        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found"));

        Boolean active = body.get("active");
        if (active == null) throw new BusinessException("MISSING_FIELD", "active (boolean) is required");

        adminPlatformService.updateSettings(
                tenantId,
                UpdatePlatformSettingsRequest.builder().killSwitchActive(active).build(),
                superAdminId
        );
        log.info("SUPER_ADMIN {} set killSwitch={} for tenant {}", superAdminId, active, tenantId);

        return ResponseEntity.ok(Map.of("tenantId", tenantId, "killSwitchActive", active));
    }

    /**
     * Global emergency shutdown — activates kill switch on ALL active tenants simultaneously.
     * Use only in platform-wide emergencies (legal, security, infrastructure).
     */
    @PostMapping("/emergency/shutdown-all")
    public ResponseEntity<Map<String, Object>> emergencyShutdownAll(
            @AuthenticationPrincipal String superAdminId) {

        List<com.lezifx.trading.domain.tenant.Tenant> activeTenants =
                tenantRepository.findByStatus(TenantStatus.ACTIVE);

        int count = 0;
        for (var tenant : activeTenants) {
            if (Boolean.TRUE.equals(tenant.getKillSwitchActive())) continue;
            try {
                adminPlatformService.updateSettings(
                        tenant.getId(),
                        UpdatePlatformSettingsRequest.builder().killSwitchActive(true).build(),
                        superAdminId
                );
                count++;
            } catch (Exception e) {
                log.error("Emergency shutdown failed for tenant {}: {}", tenant.getId(), e.getMessage());
            }
        }

        log.warn("SUPER_ADMIN {} triggered EMERGENCY SHUTDOWN — {} tenants taken offline", superAdminId, count);
        auditLogService.record(MASTER_TENANT_ID, superAdminId, "SUPER_ADMIN",
                "EMERGENCY_SHUTDOWN_ALL", "Platform", MASTER_TENANT_ID,
                null, Map.of("tenantsAffected", String.valueOf(count)), null);

        return ResponseEntity.ok(Map.of("message", "Emergency shutdown complete", "tenantsAffected", count));
    }
}