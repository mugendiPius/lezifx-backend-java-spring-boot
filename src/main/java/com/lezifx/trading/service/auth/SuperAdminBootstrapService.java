package com.lezifx.trading.service.auth;

import com.lezifx.trading.domain.enums.KycStatus;
import com.lezifx.trading.domain.enums.UserRole;
import com.lezifx.trading.domain.enums.UserStatus;
import com.lezifx.trading.domain.user.User;
import com.lezifx.trading.domain.wallet.Wallet;
import com.lezifx.trading.repository.TenantRepository;
import com.lezifx.trading.repository.UserRepository;
import com.lezifx.trading.repository.WalletRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SuperAdminBootstrapService {

    private static final UUID MASTER_TENANT_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Value("${superadmin.bootstrap-email:}")
    private String bootstrapEmail;

    @Value("${superadmin.bootstrap-password:}")
    private String bootstrapPassword;

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final WalletRepository walletRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @PostConstruct
    @Transactional
    public void bootstrap() {
        if (bootstrapEmail == null || bootstrapEmail.isBlank()) return;
        if (bootstrapPassword == null || bootstrapPassword.isBlank()) return;

        if (userRepository.findByTenantIdAndEmail(MASTER_TENANT_ID, bootstrapEmail).isPresent()) {
            return;
        }

        var masterTenant = tenantRepository.findById(MASTER_TENANT_ID).orElse(null);
        if (masterTenant == null) {
            log.error("Master tenant not found — cannot bootstrap SUPER_ADMIN. Run migrations first.");
            return;
        }

        User admin = User.builder()
            .tenant(masterTenant)
            .email(bootstrapEmail)
            .passwordHash(passwordEncoder.encode(bootstrapPassword))
            .fullName("Super Administrator")
            .role(UserRole.SUPER_ADMIN)
            .status(UserStatus.ACTIVE)
            .isMarketer(false)
            .marketerBalance(BigDecimal.ZERO)
            .kycStatus(KycStatus.NONE)
            .build();

        admin = userRepository.save(admin);

        Wallet wallet = Wallet.builder()
            .user(admin)
            .tenant(masterTenant)
            .liveBalance(BigDecimal.ZERO)
            .demoBalance(BigDecimal.ZERO)
            .version(0L)
            .build();

        walletRepository.save(wallet);

        log.info("SUPER_ADMIN bootstrapped: {}", bootstrapEmail);
    }
}