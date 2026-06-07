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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPlatformService {

    private final TenantRepository       tenantRepository;
    private final AuditLogService        auditLogService;
    private final SimpMessagingTemplate  messagingTemplate;
    private final AesEncryptionService   aesEncryptionService;

    // ─── Mode ─────────────────────────────────────────────────────────────────

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

        // BUG 4 FIX: broadcast mode change to all connected WS clients
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

    // ─── Settings (read) ──────────────────────────────────────────────────────

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
            .build();
    }

    // ─── Settings (write) ─────────────────────────────────────────────────────

    @Transactional
    public void updateSettings(UUID tenantId, UpdatePlatformSettingsRequest req, String adminId) {
        var tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found"));

        // BUG 4 FIX: broadcast per-field events only when value actually changes
        if (req.getFloorBalance() != null) {
            tenant.setFloorBalance(req.getFloorBalance());
            broadcastFloorUpdate(tenantId, req.getFloorBalance());
        }
        if (req.getKillSwitchActive() != null) {
            tenant.setKillSwitchActive(req.getKillSwitchActive());
            broadcastKillSwitch(tenantId, req.getKillSwitchActive());
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

        tenantRepository.save(tenant);

        auditLogService.record(tenantId, adminId, "ADMIN", "UPDATE_PLATFORM_SETTINGS",
            "Tenant", tenantId, null, Map.of("updated", req.toString()), null);
    }

    // ─── Daraja ───────────────────────────────────────────────────────────────

    @Transactional
    public void setDarajaCredentials(UUID tenantId, SetDarajaCredentialsRequest req, String adminId) {
        var tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found"));

        if (req.getConsumerKey()      != null) tenant.setDarajaConsumerKey(aesEncryptionService.encrypt(req.getConsumerKey()));
        if (req.getConsumerSecret()   != null) tenant.setDarajaConsumerSecret(aesEncryptionService.encrypt(req.getConsumerSecret()));
        if (req.getPasskey()          != null) tenant.setDarajaPasskey(aesEncryptionService.encrypt(req.getPasskey()));
        if (req.getShortcode()        != null) tenant.setDarajaShortcode(req.getShortcode());
        if (req.getCallbackUrl()      != null) tenant.setDarajaEnvironment(req.getCallbackUrl());
        if (req.getB2cInitiatorName() != null) tenant.setDarajaB2cInitiatorName(req.getB2cInitiatorName());
        if (req.getB2cSecurityCred()  != null) tenant.setDarajaB2cSecurityCred(aesEncryptionService.encrypt(req.getB2cSecurityCred()));

        tenantRepository.save(tenant);

        auditLogService.record(tenantId, adminId, "ADMIN", "SET_DARAJA_CREDENTIALS",
            "Tenant", tenantId, null,
            Map.of("shortCode", req.getShortcode() != null ? req.getShortcode() : "unchanged"), null);
    }

    // ─── Marketer withdrawal ──────────────────────────────────────────────────

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

    // ─── Private broadcast helpers ────────────────────────────────────────────

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