package com.lezifx.trading.web.controller.player;

import com.lezifx.trading.infrastructure.context.TenantContext;
import com.lezifx.trading.repository.UserRepository;
import com.lezifx.trading.repository.WalletRepository;
import com.lezifx.trading.service.wallet.TransactionService;
import com.lezifx.trading.web.dto.request.UpdateProfileRequest;
import com.lezifx.trading.web.dto.response.PlayerProfileResponse;
import com.lezifx.trading.web.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/player")
@RequiredArgsConstructor
public class PlayerProfileController {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransactionService transactionService;

    @GetMapping("/profile")
    public ResponseEntity<PlayerProfileResponse> getProfile(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(buildProfile(UUID.fromString(userId)));
    }

    @PutMapping("/profile")
    public ResponseEntity<PlayerProfileResponse> updateProfile(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        UUID userUuid = UUID.fromString(userId);
        var user = userRepository.findById(userUuid)
            .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName());
        }
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            user.setPhoneNumber(request.getPhoneNumber());
        }
        userRepository.save(user);
        return ResponseEntity.ok(buildProfile(userUuid));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats(
            @AuthenticationPrincipal String userId) {
        UUID userUuid = UUID.fromString(userId);
        UUID tenantId = TenantContext.get();
        return ResponseEntity.ok(transactionService.getPlayerStats(userUuid, tenantId));
    }

    private PlayerProfileResponse buildProfile(UUID userId) {
        var user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));
        var wallet = walletRepository.findByUserId(userId).orElse(null);

        return PlayerProfileResponse.builder()
            .id(user.getId())
            .tenantId(user.getTenant().getId())
            .email(user.getEmail())
            .fullName(user.getFullName())
            .phoneNumber(user.getPhoneNumber())
            .role(user.getRole().name())
            .status(user.getStatus().name())
            .kycStatus(user.getKycStatus() != null ? user.getKycStatus().name() : null)
            .isMarketer(Boolean.TRUE.equals(user.getIsMarketer()))
            .createdAt(user.getCreatedAt())
            .liveBalance(wallet != null ? wallet.getLiveBalance() : BigDecimal.ZERO)
            .demoBalance(wallet != null ? wallet.getDemoBalance() : BigDecimal.ZERO)
            .build();
    }
}