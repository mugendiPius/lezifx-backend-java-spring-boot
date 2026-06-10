package com.lezifx.trading.service.superadmin;

import com.lezifx.trading.domain.enums.PlatformMode;
import com.lezifx.trading.domain.enums.TenantStatus;
import com.lezifx.trading.domain.enums.UserRole;
import com.lezifx.trading.domain.enums.UserStatus;
import com.lezifx.trading.domain.tenant.Tenant;
import com.lezifx.trading.domain.tenant.TenantApiKey;
import com.lezifx.trading.domain.user.User;
import com.lezifx.trading.domain.wallet.Wallet;
import com.lezifx.trading.infrastructure.audit.AuditLogService;
import com.lezifx.trading.repository.TenantApiKeyRepository;
import com.lezifx.trading.repository.TenantRepository;
import com.lezifx.trading.repository.UserRepository;
import com.lezifx.trading.repository.WalletRepository;
import com.lezifx.trading.service.auth.ApiKeyService;
import com.lezifx.trading.service.mpesa.AesEncryptionService;
import com.lezifx.trading.web.dto.request.CreateTenantRequest;
import com.lezifx.trading.web.dto.request.MarketerWithdrawalConfigRequest;
import com.lezifx.trading.web.dto.request.SetDarajaCredentialsRequest;
import com.lezifx.trading.web.dto.request.UpdateTenantSettingsRequest;
import com.lezifx.trading.web.dto.response.TenantApiKeyResponse;
import com.lezifx.trading.web.dto.response.TenantDetailResponse;
import com.lezifx.trading.web.dto.response.TenantSummaryResponse;
import com.lezifx.trading.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TenantService {

    public static final UUID MASTER_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final BigDecimal COMMISSION_RATE = BigDecimal.valueOf(0.15);

    private final TenantRepository       tenantRepository;
    private final TenantApiKeyRepository tenantApiKeyRepository;
    private final UserRepository         userRepository;
    private final WalletRepository       walletRepository;
    private final ApiKeyService          apiKeyService;
    private final AesEncryptionService   aesEncryptionService;
    private final AuditLogService        auditLogService;
    private final PasswordEncoder        passwordEncoder;

    // ── Create tenant + first admin ──────────────────────────────────────────

    public TenantDetailResponse createTenant(CreateTenantRequest req, String createdBy) {
        // Validate domains — check none are already taken by another tenant
        if (req.getCustomDomains() != null) {
            for (String domain : req.getCustomDomains()) {
                String normalised = domain.toLowerCase().replaceAll("^https?://", "").replaceAll("/+$", "");
                tenantRepository.findByAllowedOriginsContaining(normalised).ifPresent(existing -> {
                    if (!MASTER_TENANT_ID.equals(existing.getId())) {
                        throw new BusinessException("DOMAIN_TAKEN",
                                "Domain '" + normalised + "' is already registered to tenant: " + existing.getBrandName());
                    }
                });
            }
        }

        BigDecimal floorBalance = req.getFloorBalance()  != null ? req.getFloorBalance()  : BigDecimal.valueOf(100_000);
        BigDecimal demoBalance  = req.getDemoBalance()   != null ? req.getDemoBalance()   : BigDecimal.valueOf(10_000);

        // Build allowed_origins from request — normalise each entry
        String[] origins = req.getCustomDomains() == null ? new String[0]
                : req.getCustomDomains().stream()
                        .map(d -> d.toLowerCase().replaceAll("^https?://", "").replaceAll("/+$", ""))
                        .filter(d -> !d.isBlank())
                        .toArray(String[]::new);

        Tenant tenant = Tenant.builder()
                .name(req.getBrandName())
                .brandName(req.getBrandName())
                .logoUrl(req.getLogoUrl())
                .faviconUrl(req.getFaviconUrl())
                .primaryColor(req.getPrimaryColor())
                .accentColor(req.getAccentColor())
                .supportEmail(req.getSupportEmail())
                .allowedOrigins(origins)
                .registrationOpen(true)
                .kycRequired(false)
                .demoBalance(demoBalance)
                .minDeposit(req.getMinDeposit()          != null ? req.getMinDeposit()          : BigDecimal.valueOf(100))
                .maxDeposit(req.getMaxDeposit()          != null ? req.getMaxDeposit()          : BigDecimal.valueOf(500_000))
                .minWithdrawal(req.getMinWithdrawal()    != null ? req.getMinWithdrawal()       : BigDecimal.valueOf(100))
                .maxWithdrawal(req.getMaxWithdrawal()    != null ? req.getMaxWithdrawal()       : BigDecimal.valueOf(100_000))
                .autoWithdrawalLimit(req.getAutoWithdrawalLimit() != null
                        ? req.getAutoWithdrawalLimit() : BigDecimal.valueOf(5_000))
                .maxConcurrentTrades(1)
                .floorBalance(floorBalance)
                .houseBalance(floorBalance)
                .commissionRate(COMMISSION_RATE)
                .platformMode(PlatformMode.NORMAL)
                .status(TenantStatus.ACTIVE)
                .killSwitchActive(false)
                .build();

        Tenant savedTenant = tenantRepository.save(tenant);
        TenantApiKey apiKey = apiKeyService.createApiKey(savedTenant, "default");

        // Create first ADMIN user for the tenant
        String fullName = (req.getAdminFullName() != null && !req.getAdminFullName().isBlank())
                ? req.getAdminFullName() : "Administrator";

        User adminUser = User.builder()
                .email(req.getAdminEmail())
                .passwordHash(passwordEncoder.encode(req.getAdminPassword()))
                .fullName(fullName)
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .isMarketer(false)
                .marketerBalance(BigDecimal.ZERO)
                .tenant(savedTenant)
                .build();

        User savedAdmin = userRepository.save(adminUser);

        Wallet wallet = Wallet.builder()
                .user(savedAdmin)
                .tenant(savedTenant)
                .liveBalance(BigDecimal.ZERO)
                .demoBalance(demoBalance)
                .version(0L)
                .build();
        walletRepository.save(wallet);

        auditLogService.record(MASTER_TENANT_ID, createdBy, "SUPER_ADMIN",
                "CREATE_TENANT", "Tenant", savedTenant.getId(),
                null, Map.of("brandName", req.getBrandName(), "adminEmail", req.getAdminEmail()), null);

        log.info("Tenant created: {} (id={})", req.getBrandName(), savedTenant.getId());

        long userCount   = userRepository.countByTenantId(savedTenant.getId());
        long activeKeys  = tenantApiKeyRepository.countByTenantIdAndIsActiveTrue(savedTenant.getId());
        return buildDetailResponse(savedTenant, apiKey.getApiKey(), userCount, activeKeys);
    }

    // ── List tenants ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<TenantSummaryResponse> listTenants(String statusFilter, String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        TenantStatus status  = parseStatus(statusFilter);
        boolean      hasSearch = search != null && !search.isBlank();

        Page<Tenant> tenants;
        if (status != null && hasSearch) {
            tenants = tenantRepository.findByIdNotAndStatusAndNameContainingIgnoreCase(
                    MASTER_TENANT_ID, status, search, pageable);
        } else if (status != null) {
            tenants = tenantRepository.findByIdNotAndStatus(MASTER_TENANT_ID, status, pageable);
        } else if (hasSearch) {
            tenants = tenantRepository.findByIdNotAndNameContainingIgnoreCase(MASTER_TENANT_ID, search, pageable);
        } else {
            tenants = tenantRepository.findByIdNot(MASTER_TENANT_ID, pageable);
        }

        return tenants.map(t -> {
            long userCount = userRepository.countByTenantId(t.getId());
            return TenantSummaryResponse.builder()
                    .id(t.getId())
                    .brandName(t.getBrandName())
                    .logoUrl(t.getLogoUrl())
                    .allowedOrigins(t.getAllowedOrigins() != null
                            ? Arrays.asList(t.getAllowedOrigins())
                            : Collections.emptyList())
                    .status(t.getStatus().name())
                    .platformMode(t.getPlatformMode().name())
                    .houseBalance(t.getHouseBalance())
                    .floorBalance(t.getFloorBalance())
                    .userCount(userCount)
                    .createdAt(t.getCreatedAt())
                    .build();
        });
    }

    // ── Get single tenant detail ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public TenantDetailResponse getTenantDetail(UUID tenantId) {
        Tenant tenant    = loadTenant(tenantId);
        long   userCount = userRepository.countByTenantId(tenantId);
        long   activeKeys = tenantApiKeyRepository.countByTenantIdAndIsActiveTrue(tenantId);
        String firstKey  = tenantApiKeyRepository.findByTenantId(tenantId).stream()
                .filter(k -> Boolean.TRUE.equals(k.getIsActive()))
                .findFirst()
                .map(k -> k.getApiKey().substring(0, 8) + "...")
                .orElse(null);
        return buildDetailResponse(tenant, firstKey, userCount, activeKeys);
    }

    // ── Update tenant status ──────────────────────────────────────────────────

    public void updateTenantStatus(UUID tenantId, TenantStatus status, String adminId) {
        guardMaster(tenantId);
        Tenant tenant    = loadTenant(tenantId);
        TenantStatus old = tenant.getStatus();
        tenant.setStatus(status);
        tenantRepository.save(tenant);
        auditLogService.record(MASTER_TENANT_ID, adminId, "SUPER_ADMIN",
                "UPDATE_TENANT_STATUS", "Tenant", tenantId,
                Map.of("status", old.name()), Map.of("status", status.name()), null);
    }

    // ── Update tenant settings ────────────────────────────────────────────────

    public void updateTenantSettings(UUID tenantId, UpdateTenantSettingsRequest req, String adminId) {
        guardMaster(tenantId);
        Tenant tenant = loadTenant(tenantId);
        if (req.getBrandName()          != null) { tenant.setBrandName(req.getBrandName()); tenant.setName(req.getBrandName()); }
        if (req.getLogoUrl()            != null) tenant.setLogoUrl(req.getLogoUrl());
        if (req.getFaviconUrl()         != null) tenant.setFaviconUrl(req.getFaviconUrl());
        if (req.getPrimaryColor()       != null) tenant.setPrimaryColor(req.getPrimaryColor());
        if (req.getAccentColor()        != null) tenant.setAccentColor(req.getAccentColor());
        if (req.getSupportEmail()       != null) tenant.setSupportEmail(req.getSupportEmail());
        if (req.getMinDeposit()         != null) tenant.setMinDeposit(req.getMinDeposit());
        if (req.getMaxDeposit()         != null) tenant.setMaxDeposit(req.getMaxDeposit());
        if (req.getMinWithdrawal()      != null) tenant.setMinWithdrawal(req.getMinWithdrawal());
        if (req.getMaxWithdrawal()      != null) tenant.setMaxWithdrawal(req.getMaxWithdrawal());
        if (req.getAutoWithdrawalLimit() != null) tenant.setAutoWithdrawalLimit(req.getAutoWithdrawalLimit());
        if (req.getDemoBalance()        != null) tenant.setDemoBalance(req.getDemoBalance());
        if (req.getMaxConcurrentTrades() != null) tenant.setMaxConcurrentTrades(req.getMaxConcurrentTrades());
        if (req.getKycRequired()        != null) tenant.setKycRequired(req.getKycRequired());
        if (req.getRegistrationOpen()   != null) tenant.setRegistrationOpen(req.getRegistrationOpen());
        tenantRepository.save(tenant);
        auditLogService.record(MASTER_TENANT_ID, adminId, "SUPER_ADMIN",
                "UPDATE_TENANT_SETTINGS", "Tenant", tenantId, null, null, null);
    }

    // ── Update allowed origins (SUPER_ADMIN version) ─────────────────────────

    public void updateAllowedOrigins(UUID tenantId, List<String> domains, String adminId) {
        guardMaster(tenantId);
        Tenant tenant = loadTenant(tenantId);
        String[] origins = domains.stream()
                .map(d -> d.toLowerCase().replaceAll("^https?://", "").replaceAll("/+$", ""))
                .filter(d -> !d.isBlank())
                .toArray(String[]::new);
        tenant.setAllowedOrigins(origins);
        tenantRepository.save(tenant);
        auditLogService.record(MASTER_TENANT_ID, adminId, "SUPER_ADMIN",
                "UPDATE_TENANT_DOMAINS", "Tenant", tenantId,
                null, Map.of("domains", String.join(", ", origins)), null);
    }

    // ── API Key management ────────────────────────────────────────────────────

    public TenantApiKeyResponse rotateApiKey(UUID tenantId, String keyLabel, String adminId) {
        guardMaster(tenantId);
        Tenant       tenant = loadTenant(tenantId);
        TenantApiKey key    = apiKeyService.createApiKey(tenant, keyLabel);
        auditLogService.record(MASTER_TENANT_ID, adminId, "SUPER_ADMIN",
                "ROTATE_API_KEY", "Tenant", tenantId, null, Map.of("label", keyLabel), null);
        return TenantApiKeyResponse.builder()
                .id(key.getId())
                .label(key.getLabel())
                .maskedKey(key.getApiKey())
                .isActive(Boolean.TRUE.equals(key.getIsActive()))
                .createdAt(key.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<TenantApiKeyResponse> listApiKeys(UUID tenantId) {
        return tenantApiKeyRepository.findByTenantId(tenantId).stream()
                .map(k -> TenantApiKeyResponse.builder()
                        .id(k.getId())
                        .label(k.getLabel())
                        .maskedKey(k.getApiKey().length() >= 8
                                ? k.getApiKey().substring(0, 8) + "..." : k.getApiKey())
                        .isActive(Boolean.TRUE.equals(k.getIsActive()))
                        .createdAt(k.getCreatedAt())
                        .revokedAt(k.getRevokedAt())
                        .build())
                .collect(Collectors.toList());
    }

    public void revokeApiKey(UUID tenantId, UUID keyId, String adminId) {
        guardMaster(tenantId);
        long activeCount = tenantApiKeyRepository.countByTenantIdAndIsActiveTrue(tenantId);
        if (activeCount <= 1) {
            throw new BusinessException("LAST_API_KEY", "Cannot revoke the last active API key");
        }
        apiKeyService.revokeApiKey(keyId);
        auditLogService.record(MASTER_TENANT_ID, adminId, "SUPER_ADMIN",
                "REVOKE_API_KEY", "TenantApiKey", keyId, null, null, null);
    }

    // ── Daraja credentials ────────────────────────────────────────────────────

    public void setDarajaCredentials(UUID tenantId, SetDarajaCredentialsRequest req, String adminId) {
        guardMaster(tenantId);
        Tenant tenant = loadTenant(tenantId);
        if (req.getConsumerKey()      != null) tenant.setDarajaConsumerKey(aesEncryptionService.encrypt(req.getConsumerKey()));
        if (req.getConsumerSecret()   != null) tenant.setDarajaConsumerSecret(aesEncryptionService.encrypt(req.getConsumerSecret()));
        if (req.getPasskey()          != null) tenant.setDarajaPasskey(aesEncryptionService.encrypt(req.getPasskey()));
        if (req.getShortcode()        != null) tenant.setDarajaShortcode(req.getShortcode());
        if (req.getB2cInitiatorName() != null) tenant.setDarajaB2cInitiatorName(req.getB2cInitiatorName());
        if (req.getB2cSecurityCred()  != null) tenant.setDarajaB2cSecurityCred(aesEncryptionService.encrypt(req.getB2cSecurityCred()));
        if (req.getEnvironment()      != null) tenant.setDarajaEnvironment(req.getEnvironment());
        tenantRepository.save(tenant);
        auditLogService.record(MASTER_TENANT_ID, adminId, "SUPER_ADMIN",
                "SET_DARAJA_CREDENTIALS", "Tenant", tenantId,
                null, Map.of("environment", req.getEnvironment() != null ? req.getEnvironment() : "unchanged"), null);
    }

    // ── Marketer withdrawal config ────────────────────────────────────────────

    public void configureMarketerWithdrawal(UUID tenantId, MarketerWithdrawalConfigRequest req, String adminId) {
        guardMaster(tenantId);
        Tenant tenant = loadTenant(tenantId);
        tenant.setMarketerWithdrawalEnabled(req.getEnabled());
        tenant.setMarketerMaxWithdrawal(req.getMaxWithdrawal());
        tenantRepository.save(tenant);
        auditLogService.record(MASTER_TENANT_ID, adminId, "SUPER_ADMIN",
                "CONFIGURE_MARKETER_WITHDRAWAL", "Tenant", tenantId,
                null, Map.of("enabled", req.getEnabled(), "maxWithdrawal", req.getMaxWithdrawal()), null);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Tenant loadTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found"));
    }

    /** Prevents SUPER_ADMIN from accidentally mutating the master tenant via tenant endpoints. */
    private void guardMaster(UUID tenantId) {
        if (MASTER_TENANT_ID.equals(tenantId)) {
            throw new BusinessException("FORBIDDEN", "Cannot modify master tenant via tenant management endpoints");
        }
    }

    private TenantStatus parseStatus(String statusFilter) {
        if (statusFilter == null || statusFilter.isBlank()) return null;
        try { return TenantStatus.valueOf(statusFilter.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }

    private TenantDetailResponse buildDetailResponse(Tenant t, String apiKey, long userCount, long activeKeys) {
        BigDecimal ratio = (t.getFloorBalance() != null && t.getFloorBalance().compareTo(BigDecimal.ZERO) > 0)
                ? t.getHouseBalance().divide(t.getFloorBalance(), 4, RoundingMode.HALF_UP)
                : new BigDecimal("999");
        return TenantDetailResponse.builder()
                .id(t.getId())
                .brandName(t.getBrandName())
                .logoUrl(t.getLogoUrl())
                .faviconUrl(t.getFaviconUrl())
                .primaryColor(t.getPrimaryColor())
                .accentColor(t.getAccentColor())
                .supportEmail(t.getSupportEmail())
                .allowedOrigins(t.getAllowedOrigins() != null
                        ? Arrays.asList(t.getAllowedOrigins())
                        : Collections.emptyList())
                .status(t.getStatus().name())
                .platformMode(t.getPlatformMode().name())
                .houseBalance(t.getHouseBalance())
                .floorBalance(t.getFloorBalance())
                .houseRatio(ratio)
                .minDeposit(t.getMinDeposit())
                .maxDeposit(t.getMaxDeposit())
                .minWithdrawal(t.getMinWithdrawal())
                .maxWithdrawal(t.getMaxWithdrawal())
                .autoWithdrawalLimit(t.getAutoWithdrawalLimit())
                .demoBalance(t.getDemoBalance())
                .maxConcurrentTrades(t.getMaxConcurrentTrades())
                .kycRequired(t.getKycRequired())
                .registrationOpen(t.getRegistrationOpen())
                .userCount(userCount)
                .activeApiKeys(activeKeys)
                .apiKey(apiKey)
                .createdAt(t.getCreatedAt())
                .build();
    }
}