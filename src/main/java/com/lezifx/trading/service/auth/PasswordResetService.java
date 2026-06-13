package com.lezifx.trading.service.auth;

import com.lezifx.trading.domain.tenant.Tenant;
import com.lezifx.trading.domain.user.PasswordResetToken;
import com.lezifx.trading.domain.user.User;
import com.lezifx.trading.repository.PasswordResetTokenRepository;
import com.lezifx.trading.repository.TenantRepository;
import com.lezifx.trading.repository.UserRepository;
import com.lezifx.trading.service.notification.EmailService;
import com.lezifx.trading.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private static final int OTP_LENGTH     = 6;
    private static final int EXPIRY_MINUTES = 10;

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository               userRepository;
    private final TenantRepository             tenantRepository;
    private final EmailService                 emailService;
    private final BCryptPasswordEncoder        passwordEncoder;

    /**
     * Step 1 — request OTP.
     * Always returns success to avoid leaking whether the email exists.
     */
    @Transactional
    public void requestReset(String email, UUID tenantId) {
        String normalised = email.trim().toLowerCase();

        var userOpt = userRepository.findByTenantIdAndEmail(tenantId, normalised);
        if (userOpt.isEmpty()) {
            log.info("Password reset requested for unknown email {} on tenant {}", normalised, tenantId);
            return; // silent — don't reveal user existence
        }

        User user   = userOpt.get();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found"));

        // Invalidate any previous pending tokens for this email
        tokenRepository.invalidateAll(tenantId, normalised);

        String otp     = generateOtp();
        String otpHash = hash(otp);

        PasswordResetToken token = PasswordResetToken.builder()
                .tenant(tenant)
                .email(normalised)
                .otpHash(otpHash)
                .expiresAt(Instant.now().plusSeconds(EXPIRY_MINUTES * 60L))
                .used(false)
                .build();

        tokenRepository.save(token);

        String brandName = tenant.getBrandName() != null ? tenant.getBrandName() : "LeziFx";
        emailService.sendPasswordResetOtp(normalised, user.getFullName(), otp, brandName);

        log.info("Password reset OTP issued for {} on tenant {}", normalised, tenantId);
    }

    /**
     * Step 2 — verify OTP and set new password.
     */
    @Transactional
    public void confirmReset(String email, String otp, String newPassword, UUID tenantId) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new BusinessException("WEAK_PASSWORD", "Password must be at least 6 characters");
        }

        String normalised = email.trim().toLowerCase();
        String otpHash    = hash(otp.trim());

        PasswordResetToken token = tokenRepository
                .findValidToken(tenantId, normalised, Instant.now())
                .orElseThrow(() -> new BusinessException("INVALID_OTP",
                        "Code is invalid or has expired. Please request a new one."));

        if (!token.getOtpHash().equals(otpHash)) {
            throw new BusinessException("INVALID_OTP", "Code is incorrect. Please check and try again.");
        }

        // Mark used
        token.setUsed(true);
        tokenRepository.save(token);

        // Update password
        User user = userRepository.findByTenantIdAndEmail(tenantId, normalised)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        log.info("Password successfully reset for {} on tenant {}", normalised, tenantId);
    }

    /** Purge expired tokens daily to keep the table clean */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        tokenRepository.deleteExpired(Instant.now());
    }

    private static String generateOtp() {
        SecureRandom rng = new SecureRandom();
        int code = 100_000 + rng.nextInt(900_000);
        return String.valueOf(code);
    }

    private static String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }
}
