package com.lezifx.trading.web.controller;

import com.lezifx.trading.infrastructure.context.TenantContext;
import com.lezifx.trading.repository.UserRepository;
import com.lezifx.trading.service.auth.AuthService;
import com.lezifx.trading.service.auth.PasswordResetService;
import com.lezifx.trading.web.dto.request.LoginRequest;
import com.lezifx.trading.web.dto.request.RefreshRequest;
import com.lezifx.trading.web.dto.request.RegisterRequest;
import com.lezifx.trading.web.dto.response.LoginResponse;
import com.lezifx.trading.web.dto.response.RegisterResponse;
import com.lezifx.trading.web.dto.response.UserResponse;
import com.lezifx.trading.web.exception.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final UserRepository userRepository;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        UUID tenantId = TenantContext.get();
        return ResponseEntity.ok(authService.login(request.getEmail(), request.getPassword(), tenantId));
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        UUID tenantId = TenantContext.get();
        RegisterResponse response = authService.register(
            request.getEmail(),
            request.getPassword(),
            request.getFullName(),
            request.getPhoneNumber(),
            tenantId
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refreshTokens(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal String userId) {
        authService.logout(userId);
        return ResponseEntity.noContent().build();
    }

    // ── Password reset ────────────────────────────────────────────────────────

    record ForgotPasswordRequest(@NotBlank @Email String email) {}

    record ResetPasswordRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6, max = 6) String otp,
        @NotBlank @Size(min = 6, max = 128) String newPassword
    ) {}

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest req) {
        UUID tenantId = TenantContext.get();
        passwordResetService.requestReset(req.email(), tenantId);
        return ResponseEntity.ok(Map.of("message",
                "If that email is registered, a reset code has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest req) {
        UUID tenantId = TenantContext.get();
        passwordResetService.confirmReset(req.email(), req.otp(), req.newPassword(), tenantId);
        return ResponseEntity.ok(Map.of("message", "Password updated successfully."));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal String userId) {
        var user = userRepository.findById(UUID.fromString(userId))
            .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));

        UserResponse response = UserResponse.builder()
            .id(user.getId())
            .tenantId(user.getTenant().getId())
            .email(user.getEmail())
            .fullName(user.getFullName())
            .phoneNumber(user.getPhoneNumber())
            .role(user.getRole().name())
            .status(user.getStatus().name())
            .isMarketer(Boolean.TRUE.equals(user.getIsMarketer()))
            .createdAt(user.getCreatedAt())
            .build();

        return ResponseEntity.ok(response);
    }
}