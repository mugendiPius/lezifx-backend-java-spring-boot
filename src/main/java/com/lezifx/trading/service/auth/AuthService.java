package com.lezifx.trading.service.auth;

import com.lezifx.trading.domain.enums.KycStatus;
import com.lezifx.trading.domain.enums.UserRole;
import com.lezifx.trading.domain.enums.UserStatus;
import com.lezifx.trading.domain.user.RefreshToken;
import com.lezifx.trading.domain.user.User;
import com.lezifx.trading.domain.wallet.Wallet;
import com.lezifx.trading.repository.RefreshTokenRepository;
import com.lezifx.trading.repository.TenantRepository;
import com.lezifx.trading.repository.UserRepository;
import com.lezifx.trading.repository.WalletRepository;
import com.lezifx.trading.web.dto.response.LoginResponse;
import com.lezifx.trading.web.dto.response.RegisterResponse;
import com.lezifx.trading.web.dto.response.UserResponse;
import com.lezifx.trading.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final UUID MASTER_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final WalletRepository walletRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder;

    @Transactional
    public LoginResponse login(String email, String password, UUID tenantId) {
        log.info("Login attempt: email={} tenantId={}", email, tenantId);

        User user = userRepository.findByTenantIdAndEmail(tenantId, email)
                .orElseThrow(() -> {
                    log.warn("Login failed - user not found: email={} tenantId={}", email, tenantId);
                    return new BusinessException("USER_NOT_FOUND", "No account found with that email");
                });

        log.info("User found: id={} role={} status={}", user.getId(), user.getRole(), user.getStatus());

        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("Login failed - account not active: email={} status={}", email, user.getStatus());
            throw new BusinessException("ACCOUNT_SUSPENDED", "Your account is not active");
        }

//        if (user.getRole() == UserRole.SUPER_ADMIN) {
//            throw new BusinessException("USE_SUPERADMIN_ENDPOINT",
//                    "Super admin accounts must log in via /api/v1/superadmin/auth/login");
//        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            log.warn("Login failed - wrong password: email={}", email);
            throw new BusinessException("INVALID_CREDENTIALS", "Incorrect password");
        }

        log.info("Login successful: email={} role={}", email, user.getRole());
        return buildLoginResponse(user);
    }

    @Transactional
    public LoginResponse superAdminLogin(String email, String password) {
        log.info("SuperAdmin login attempt: email={}", email);

        User user = userRepository.findByTenantIdAndEmail(MASTER_TENANT_ID, email)
                .orElseThrow(() -> {
                    log.warn("SuperAdmin login failed - not found: email={}", email);
                    return new BusinessException("UNAUTHORIZED", "Invalid credentials");
                });

        if (user.getRole() != UserRole.SUPER_ADMIN) {
            log.warn("SuperAdmin login failed - not SUPER_ADMIN role: email={} role={}", email, user.getRole());
            throw new BusinessException("UNAUTHORIZED", "Invalid credentials");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("ACCOUNT_SUSPENDED", "Account is not active");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            log.warn("SuperAdmin login failed - wrong password: email={}", email);
            throw new BusinessException("UNAUTHORIZED", "Invalid credentials");
        }

        log.info("SuperAdmin login successful: email={}", email);
        return buildLoginResponse(user);
    }

    @Transactional
    public RegisterResponse register(String email, String password,
                                     String fullName, String phone, UUID tenantId) {
        log.info("Register attempt: email={} tenantId={}", email, tenantId);

        var tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found"));

        if (Boolean.FALSE.equals(tenant.getRegistrationOpen())) {
            throw new BusinessException("REGISTRATION_CLOSED", "Registration is currently closed for this platform");
        }

        if (userRepository.findByTenantIdAndEmail(tenantId, email).isPresent()) {
            throw new BusinessException("EMAIL_TAKEN", "An account with this email already exists");
        }

        User user = User.builder()
                .tenant(tenant)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .fullName(fullName)
                .phoneNumber(phone)
                .role(UserRole.PLAYER)
                .status(UserStatus.ACTIVE)
                .isMarketer(false)
                .marketerBalance(BigDecimal.ZERO)
                .kycStatus(KycStatus.NONE)
                .build();

        user = userRepository.save(user);
        log.info("User registered: id={} email={} tenantId={}", user.getId(), email, tenantId);

        BigDecimal demoBalance = tenant.getDemoBalance() != null
                ? tenant.getDemoBalance()
                : BigDecimal.valueOf(10000);

        Wallet wallet = Wallet.builder()
                .user(user)
                .tenant(tenant)
                .liveBalance(BigDecimal.ZERO)
                .demoBalance(demoBalance)
                .version(0L)
                .build();

        walletRepository.save(wallet);

        return RegisterResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .message("Registration successful")
                .build();
    }

    @Transactional
    public LoginResponse refreshTokens(String refreshToken) {
        String tokenHash = hashToken(refreshToken);

        RefreshToken stored = refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash)
                .orElseThrow(() -> new BusinessException("INVALID_REFRESH_TOKEN",
                        "Refresh token is invalid or has been revoked"));

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("REFRESH_TOKEN_EXPIRED",
                    "Refresh token has expired, please log in again");
        }

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        User user = stored.getUser();
        log.info("Token refreshed for user: id={}", user.getId());
        try {
            return buildLoginResponse(user);
        } catch (DataIntegrityViolationException e) {
            // Two tabs refreshed at the exact same second — jti claim now prevents this,
            // but guard here so a race never returns 500.
            throw new BusinessException("CONCURRENT_REFRESH",
                    "Concurrent refresh detected, please retry");
        }
    }

    @Transactional
    public void logout(String userId) {
        log.info("Logout: userId={}", userId);
        refreshTokenRepository.deleteByUserId(UUID.fromString(userId));
    }

    private LoginResponse buildLoginResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = jwtService.generateRefreshToken(user);

        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .user(user)
                .tokenHash(hashToken(rawRefreshToken))
                .expiresAt(Instant.now().plusSeconds(604800))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshTokenEntity);

        UserResponse userResponse = UserResponse.builder()
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

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .accessTokenExpiresIn(jwtService.getAccessTokenExpiry())
                .user(userResponse)
                .build();
    }

    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}