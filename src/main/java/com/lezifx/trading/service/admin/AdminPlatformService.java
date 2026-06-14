package com.lezifx.trading.service.admin;

import com.lezifx.trading.domain.enums.PlatformMode;
import com.lezifx.trading.infrastructure.audit.AuditLogService;
import com.lezifx.trading.repository.TenantRepository;
import com.lezifx.trading.service.mpesa.AesEncryptionService;
import com.lezifx.trading.web.dto.event.PlatformEvent;
import com.lezifx.trading.web.dto.request.MarketerWithdrawalConfigRequest;
import com.lezifx.trading.web.dto.request.SetDarajaCredentialsRequest;
import com.lezifx.trading.web.dto.request.UpdatePlatformSettingsRequest;
import com.lezifx.trading.web.dto.response.AdminPlatformSettingsResponse;
import com.lezifx.trading.web.exception.BusinessException;
import com.lezifx.trading.web.filter.KillSwitchFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPlatformService {

    private final TenantRepository      tenantRepository;
    private final AuditLogService       auditLogService;
    private final SimpMessagingTemplate messagingTemplate;
    private final AesEncryptionService  aesEncryptionService;

    // @Lazy to avoid circular bean dependency during startup
    @Lazy
    @Autowired
    private KillSwitchFilter killSwitchFilter;

    // ── Mode ──────────────────────────────────────────────────────────────────

    @Transactional
    public void setMode(UUID tenantId, PlatformMode mode, String adminId) {
        var tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found"));

        PlatformMode oldMode = tenant.getPlatformMode();
        tenant.setPlatformMode(mode);
        tenantRepository.save(tenant);

        auditLogService.record(tenantId, adminId, "ADMIN", "SET_PLATFORM_MODE",
            "Tenant", tenantId,
            Map.of("mode", oldMode.name()),
            Map.of("mode", mode.name()), null);

        try {
            PlatformEvent event = PlatformEvent.builder()
                .event("MODE_CHANGE")
                .mode(mode.name())
                .tenantId(tenantId)
                .timestamp(Instant.now())
                .build();
            messagingTemplate.convertAndSend("/topic/" + tenantId + "/platform", event);
        } catch (Exception e) {
            log.warn("Could not broadcast mode change for tenant {}: {}", tenantId, e.getMessage());
        }
    }

    // ── Settings (read) ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AdminPlatformSettingsResponse getSettings(UUID tenantId) {
        var tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found"));

        return AdminPlatformSettingsResponse.builder()
            .platformMode(tenant.getPlatformMode().name())
            .floorBalance(tenant.getFloorBalance())
            .killSwitchActive(Boolean.TRUE.equals(tenant.getKillSwitchActive()))
            .autoWithdrawalLimit(tenant.getAutoWithdrawalLimit())
            .minDeposit(tenant.getMinDeposit())
            .maxDeposit(tenant.getMaxDeposit())
            .minWithdrawal(tenant.getMinWithdrawal())
            .maxWithdrawal(tenant.getMaxWithdrawal())
            .maxConcurrentTrades(tenant.getMaxConcurrentTrades())
            .demoBalance(tenant.getDemoBalance())
            .kycRequired(tenant.getKycRequired())
            .registrationOpen(tenant.getRegistrationOpen())
            .marketerWithdrawalEnabled(tenant.getMarketerWithdrawalEnabled())
            .marketerMaxWithdrawal(tenant.getMarketerMaxWithdrawal())
            .brandName(tenant.getBrandName())
            .primaryColor(tenant.getPrimaryColor())
            .accentColor(tenant.getAccentColor())
            .tagline(tenant.getTagline())
            .logoUrl(tenant.getLogoUrl())
            .faviconUrl(tenant.getFaviconUrl())
            .build();
    }

    // ── Settings (write) ──────────────────────────────────────────────────────

    @Transactional
    public void updateSettings(UUID tenantId, UpdatePlatformSettingsRequest req, String adminId) {
        var tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found"));

        if (req.getFloorBalance()        != null) {
            tenant.setFloorBalance(req.getFloorBalance());
            broadcastFloorUpdate(tenantId, req.getFloorBalance());
        }
        if (req.getKillSwitchActive()    != null) {
            tenant.setKillSwitchActive(req.getKillSwitchActive());
            broadcastKillSwitch(tenantId, req.getKillSwitchActive());
            killSwitchFilter.invalidateCache(tenantId);
        }
        if (req.getAutoWithdrawalLimit() != null) tenant.setAutoWithdrawalLimit(req.getAutoWithdrawalLimit());
        if (req.getMinDeposit()          != null) tenant.setMinDeposit(req.getMinDeposit());
        if (req.getMaxDeposit()          != null) tenant.setMaxDeposit(req.getMaxDeposit());
        if (req.getMinWithdrawal()       != null) tenant.setMinWithdrawal(req.getMinWithdrawal());
        if (req.getMaxWithdrawal()       != null) tenant.setMaxWithdrawal(req.getMaxWithdrawal());
        if (req.getMaxConcurrentTrades() != null) tenant.setMaxConcurrentTrades(req.getMaxConcurrentTrades());
        if (req.getDemoBalance()         != null) tenant.setDemoBalance(req.getDemoBalance());
        if (req.getKycRequired()         != null) tenant.setKycRequired(req.getKycRequired());
        if (req.getRegistrationOpen()    != null) tenant.setRegistrationOpen(req.getRegistrationOpen());
        // Branding
        if (req.getBrandName()    != null && !req.getBrandName().isBlank())
            tenant.setBrandName(req.getBrandName());
        if (req.getPrimaryColor() != null && !req.getPrimaryColor().isBlank())
            tenant.setPrimaryColor(req.getPrimaryColor());
        if (req.getAccentColor()  != null && !req.getAccentColor().isBlank())
            tenant.setAccentColor(req.getAccentColor());
        if (req.getTagline()      != null)
            tenant.setTagline(req.getTagline().isBlank() ? null : req.getTagline().trim());
        if (req.getLogoUrl()      != null)
            tenant.setLogoUrl(req.getLogoUrl().isBlank() ? null : req.getLogoUrl().trim());
        if (req.getFaviconUrl()   != null)
            tenant.setFaviconUrl(req.getFaviconUrl().isBlank() ? null : req.getFaviconUrl().trim());

        tenantRepository.save(tenant);

        auditLogService.record(tenantId, adminId, "ADMIN", "UPDATE_PLATFORM_SETTINGS",
            "Tenant", tenantId, null, Map.of("updated", req.toString()), null);
    }

    // ── Domains ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<String> getDomains(UUID tenantId) {
        var tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found"));
        String[] origins = tenant.getAllowedOrigins();
        return origins != null ? Arrays.asList(origins) : List.of();
    }

    @Transactional
    public void updateDomains(UUID tenantId, List<String> domains, String adminId) {
        var tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found"));

        String[] normalised = domains.stream()
            .map(d -> d.trim().toLowerCase().replaceFirst("^https?://", "").replaceAll("/+$", ""))
            .filter(d -> !d.isBlank())
            .distinct()
            .toArray(String[]::new);

        tenant.setAllowedOrigins(normalised);
        tenantRepository.save(tenant);

        auditLogService.record(tenantId, adminId, "ADMIN", "UPDATE_DOMAINS",
            "Tenant", tenantId, null, Map.of("domains", String.join(",", normalised)), null);
    }

    // ── Daraja ────────────────────────────────────────────────────────────────

    @Transactional
    public void setDarajaCredentials(UUID tenantId, SetDarajaCredentialsRequest req, String adminId) {
        var tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found"));

        if (req.getConsumerKey()      != null) tenant.setDarajaConsumerKey(aesEncryptionService.encrypt(req.getConsumerKey()));
        if (req.getConsumerSecret()   != null) tenant.setDarajaConsumerSecret(aesEncryptionService.encrypt(req.getConsumerSecret()));
        if (req.getPasskey()          != null) tenant.setDarajaPasskey(aesEncryptionService.encrypt(req.getPasskey()));
        if (req.getShortcode()        != null) tenant.setDarajaShortcode(req.getShortcode());
        if (req.getEnvironment()      != null) tenant.setDarajaEnvironment(req.getEnvironment());
        if (req.getB2cInitiatorName() != null) tenant.setDarajaB2cInitiatorName(req.getB2cInitiatorName());
        if (req.getB2cSecurityCred()  != null) tenant.setDarajaB2cSecurityCred(aesEncryptionService.encrypt(req.getB2cSecurityCred()));

        tenantRepository.save(tenant);

        auditLogService.record(tenantId, adminId, "ADMIN", "SET_DARAJA_CREDENTIALS",
            "Tenant", tenantId, null,
            Map.of("shortCode", req.getShortcode() != null ? req.getShortcode() : "unchanged"), null);
    }

    // ── Marketer withdrawal ───────────────────────────────────────────────────

    @Transactional
    public AdminPlatformSettingsResponse configureMarketerWithdrawal(
            UUID tenantId, MarketerWithdrawalConfigRequest req, String adminId) {
        var tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found"));

        tenant.setMarketerWithdrawalEnabled(req.getEnabled());
        tenant.setMarketerMaxWithdrawal(req.getMaxWithdrawal());
        tenantRepository.save(tenant);

        auditLogService.record(tenantId, adminId, "ADMIN", "CONFIGURE_MARKETER_WITHDRAWAL",
            "Tenant", tenantId, null,
            Map.of("enabled", req.getEnabled(), "maxWithdrawal", req.getMaxWithdrawal()), null);

        return getSettings(tenantId);
    }

    // ── Private broadcast helpers ─────────────────────────────────────────────

    private void broadcastFloorUpdate(UUID tenantId, BigDecimal floorBalance) {
        try {
            messagingTemplate.convertAndSend(
                "/topic/" + tenantId + "/platform",
                PlatformEvent.builder()
                    .event("FLOOR_UPDATE")
                    .floorBalance(floorBalance)
                    .tenantId(tenantId)
                    .timestamp(Instant.now())
                    .build()
            );
        } catch (Exception e) {
            log.warn("Could not broadcast floor update for tenant {}: {}", tenantId, e.getMessage());
        }
    }

    private void broadcastKillSwitch(UUID tenantId, boolean active) {
        try {
            messagingTemplate.convertAndSend(
                "/topic/" + tenantId + "/platform",
                PlatformEvent.builder()
                    .event("KILL_SWITCH")
                    .killSwitch(active)
                    .tenantId(tenantId)
                    .timestamp(Instant.now())
                    .build()
            );
        } catch (Exception e) {
            log.warn("Could not broadcast kill switch for tenant {}: {}", tenantId, e.getMessage());
        }
    }
}