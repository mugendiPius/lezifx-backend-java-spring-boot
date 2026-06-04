package com.lezifx.trading.web.controller.superadmin;

import com.lezifx.trading.service.auth.AuthService;
import com.lezifx.trading.web.dto.request.LoginRequest;
import com.lezifx.trading.web.dto.request.RefreshRequest;
import com.lezifx.trading.web.dto.response.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/superadmin/auth")
@RequiredArgsConstructor
public class SuperAdminAuthController {

    private static final String MASTER_API_KEY = "lzfx_master_00000000000000000000000000000001";

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                    HttpServletRequest httpRequest) {
        String apiKey = httpRequest.getHeader("X-API-Key");
        if (!MASTER_API_KEY.equals(apiKey)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "FORBIDDEN", "message", "Invalid master API key"));
        }

        LoginResponse response = authService.superAdminLogin(request.getEmail(), request.getPassword());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refreshTokens(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@AuthenticationPrincipal String userId) {
        authService.logout(userId);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }
}