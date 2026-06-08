package com.lezifx.trading.web.controller.admin;

import com.lezifx.trading.infrastructure.audit.AuditLogService;
import com.lezifx.trading.infrastructure.context.TenantContext;
import com.lezifx.trading.service.admin.AdminUserService;
import com.lezifx.trading.web.dto.request.RegisterRequest;
import com.lezifx.trading.web.dto.response.AdminUserResponse;
import com.lezifx.trading.web.exception.BusinessException;
import com.lezifx.trading.domain.enums.UserRole;
import com.lezifx.trading.domain.enums.UserStatus;
import com.lezifx.trading.domain.user.User;
import com.lezifx.trading.domain.wallet.Wallet;
import com.lezifx.trading.repository.TenantRepository;
import com.lezifx.trading.repository.UserRepository;
import com.lezifx.trading.repository.WalletRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminMarketerController {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TenantRepository tenantRepository;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/marketers")
    public ResponseEntity<List<AdminUserResponse>> listMarketers() {
        UUID tenantId = TenantContext.get();
        List<AdminUserResponse> marketers = userRepository
            .findByTenantIdAndIsMarketerTrue(tenantId)
            .stream()
            .map(u -> {
                var wallet = walletRepository.findByUserId(u.getId()).orElse(null);
                return AdminUserResponse.builder()
                    .id(u.getId())
                    .tenantId(u.getTenant().getId())
                    .email(u.getEmail())
                    .fullName(u.getFullName())
                    .phoneNumber(u.getPhoneNumber())
                    .role(u.getRole().name())
                    .status(u.getStatus().name())
                    .kycStatus(u.getKycStatus() != null ? u.getKycStatus().name() : null)
                    .isMarketer(true)
                    .liveBalance(wallet != null ? wallet.getLiveBalance() : BigDecimal.ZERO)
                    .demoBalance(wallet != null ? wallet.getDemoBalance() : BigDecimal.ZERO)
                    .createdAt(u.getCreatedAt())
                    .lastLoginAt(u.getLastLoginAt())
                    .build();
            })
            .collect(Collectors.toList());
        return ResponseEntity.ok(marketers);
    }

    @PostMapping("/marketers")
    public ResponseEntity<AdminUserResponse> createMarketer(
            @RequestBody Map<String, String> req,
            @AuthenticationPrincipal String adminId) {
        UUID tenantId = TenantContext.get();
        var tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found"));

        String email = req.get("email");
        String password = req.get("password");
        String fullName = req.get("fullName");
        String phone = req.get("phoneNumber");

        if (email == null || password == null || fullName == null) {
            throw new BusinessException("MISSING_FIELDS", "email, password and fullName are required");
        }
        if (userRepository.findByTenantIdAndEmail(tenantId, email).isPresent()) {
            throw new BusinessException("EMAIL_TAKEN", "Email already registered in this tenant");
        }

        BigDecimal defaultMarketerBalance = tenant.getDefaultMarketerBalance() != null
            ? tenant.getDefaultMarketerBalance() : BigDecimal.valueOf(10_000);

        User user = User.builder()
            .tenant(tenant)
            .email(email)
            .passwordHash(passwordEncoder.encode(password))
            .fullName(fullName)
            .phoneNumber(phone)
            .role(UserRole.PLAYER)
            .status(UserStatus.ACTIVE)
            .isMarketer(true)
            .marketerBalance(defaultMarketerBalance)
            .build();
        user = userRepository.save(user);

        BigDecimal demoBalance = tenant.getDemoBalance() != null
            ? tenant.getDemoBalance() : BigDecimal.valueOf(10_000);

        Wallet wallet = Wallet.builder()
            .user(user)
            .tenant(tenant)
            .liveBalance(BigDecimal.ZERO)
            .demoBalance(demoBalance)
            .version(0L)
            .build();
        walletRepository.save(wallet);

        auditLogService.record(tenantId, adminId, "ADMIN", "CREATE_MARKETER",
            "User", user.getId(), null, Map.of("email", email), null);

        return ResponseEntity.status(HttpStatus.CREATED).body(AdminUserResponse.builder()
            .id(user.getId())
            .tenantId(tenantId)
            .email(user.getEmail())
            .fullName(user.getFullName())
            .phoneNumber(user.getPhoneNumber())
            .role(user.getRole().name())
            .status(user.getStatus().name())
            .isMarketer(true)
            .liveBalance(BigDecimal.ZERO)
            .demoBalance(demoBalance)
            .createdAt(user.getCreatedAt())
            .build());
    }

    @PutMapping("/marketers/{userId}/convert")
    public ResponseEntity<Map<String, String>> convertToMarketer(
            @PathVariable UUID userId,
            @AuthenticationPrincipal String adminId) {
        UUID tenantId = TenantContext.get();
        var tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found"));
        var user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));
        if (!user.getTenant().getId().equals(tenantId)) {
            throw new BusinessException("ACCESS_DENIED", "Resource not in your tenant");
        }
        if (Boolean.TRUE.equals(user.getIsMarketer())) {
            return ResponseEntity.ok(Map.of("message", "User is already a marketer"));
        }

        BigDecimal defaultMarketerBalance = tenant.getDefaultMarketerBalance() != null
            ? tenant.getDefaultMarketerBalance() : BigDecimal.valueOf(10_000);

        user.setIsMarketer(true);
        if (user.getMarketerBalance() == null || user.getMarketerBalance().compareTo(BigDecimal.ZERO) == 0) {
            user.setMarketerBalance(defaultMarketerBalance);
        }
        userRepository.save(user);

        auditLogService.record(tenantId, adminId, "ADMIN", "CONVERT_TO_MARKETER",
            "User", userId, Map.of("isMarketer", false), Map.of("isMarketer", true), null);

        return ResponseEntity.ok(Map.of("message", "User converted to marketer"));
    }
}