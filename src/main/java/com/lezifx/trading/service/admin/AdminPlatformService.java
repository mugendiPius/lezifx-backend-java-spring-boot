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

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPlatformService {

    private final TenantRepository tenantRepository;
    private final AuditLogService auditLogService;
    private final SimpMessagingTemplate messagingTemplate;
    private final AesEncryptionService aesEncryptionService;

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

    @Transactional(readOnly = true)
    public AdminPlatformSettingsResponse getSettings(UUID tenantId) {
        var tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found"));

        return AdminPlatformSettingsResponse.builder()
            .platformMode(tenant.getPlatformMode().name())
            .floorBalance(tenant.getFloorBalance())
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

    @Transactional
    public void updateSettings(UUID tenantId, UpdatePlatformSettingsRequest req, String adminId) {
        var tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found"));

        if (req.getFloorBalance() != null) tenant.setFloorBalance(req.getFloorBalance());
        if (req.getAutoWithdrawalLimit() != null) tenant.setAutoWithdrawalLimit(req.getAutoWithdrawalLimit());
        if (req.getMinDeposit() != null) tenant.setMinDeposit(req.getMinDeposit());
        if (req.getMaxDeposit() != null) tenant.setMaxDeposit(req.getMaxDeposit());
        if (req.getMinWithdrawal() != null) tenant.setMinWithdrawal(req.getMinWithdrawal());
        if (req.getMaxWithdrawal() != null) tenant.setMaxWithdrawal(req.getMaxWithdrawal());
        if (req.getMaxConcurrentTrades() != null) tenant.setMaxConcurrentTrades(req.getMaxConcurrentTrades());
        if (req.getDemoBalance() != null) tenant.setDemoBalance(req.getDemoBalance());
        if (req.getKycRequired() != null) tenant.setKycRequired(req.getKycRequired());
        if (req.getRegistrationOpen() != null) tenant.setRegistrationOpen(req.getRegistrationOpen());

        tenantRepository.save(tenant);

        auditLogService.record(tenantId, adminId, "ADMIN", "UPDATE_PLATFORM_SETTINGS",
            "Tenant", tenantId, null, Map.of("updated", req.toString()), null);
    }

    @Transactional
    public void setDarajaCredentials(UUID tenantId, SetDarajaCredentialsRequest req, String adminId) {
        var tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "Tenant not found"));

        if (req.getConsumerKey() != null)
            tenant.setDarajaConsumerKey(aesEncryptionService.encrypt(req.getConsumerKey()));
        if (req.getConsumerSecret() != null)
            tenant.setDarajaConsumerSecret(aesEncryptionService.encrypt(req.getConsumerSecret()));
        if (req.getPasskey() != null)
            tenant.setDarajaPasskey(aesEncryptionService.encrypt(req.getPasskey()));
        if (req.getShortcode() != null)
            tenant.setDarajaShortcode(req.getShortcode());
        if (req.getB2cInitiatorName() != null)
            tenant.setDarajaB2cInitiatorName(req.getB2cInitiatorName());
        if (req.getB2cSecurityCred() != null)
            tenant.setDarajaB2cSecurityCred(aesEncryptionService.encrypt(req.getB2cSecurityCred()));
        if (req.getEnvironment() != null)
            tenant.setDarajaEnvironment(req.getEnvironment());

        tenantRepository.save(tenant);

        auditLogService.record(tenantId, adminId, "ADMIN", "SET_DARAJA_CREDENTIALS",
            "Tenant", tenantId, null,
            Map.of("environment", req.getEnvironment() != null ? req.getEnvironment() : "unchanged"), null);
    }

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
}