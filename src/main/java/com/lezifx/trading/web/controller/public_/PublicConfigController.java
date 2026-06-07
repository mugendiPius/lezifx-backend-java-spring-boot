package com.lezifx.trading.web.controller.public_;

import com.lezifx.trading.domain.tenant.Tenant;
import com.lezifx.trading.repository.TenantApiKeyRepository;
import com.lezifx.trading.repository.TenantRepository;
import com.lezifx.trading.service.platform.PlatformModeService;
import com.lezifx.trading.web.dto.response.TenantConfigResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicConfigController {

    private static final UUID MASTER_TENANT_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final TenantRepository       tenantRepository;
    private final TenantApiKeyRepository tenantApiKeyRepository;
    private final PlatformModeService    platformModeService;

    @GetMapping("/config")
    public TenantConfigResponse getConfig(HttpServletRequest request) {
        String domain = resolveDomain(request);

        Tenant tenant = null;
        if (domain != null && !domain.isBlank()) {
            tenant = tenantRepository.findByCustomDomain(domain).orElse(null);
        }

        if (tenant == null) {
            tenant = tenantRepository.findById(MASTER_TENANT_ID)
                .orElseThrow(() -> new RuntimeException("Master tenant not found"));
        }

        return buildConfigResponse(tenant);
    }

    private String resolveDomain(HttpServletRequest request) {
        String xDomain = request.getHeader("X-Domain");
        if (xDomain != null && !xDomain.isBlank()) return xDomain.trim();

        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank()) return stripProtocol(origin);

        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank()) return stripProtocol(referer);

        return null;
    }

    private String stripProtocol(String url) {
        String stripped = url.replaceFirst("^https?://", "");
        int slashIdx = stripped.indexOf('/');
        return slashIdx > 0 ? stripped.substring(0, slashIdx) : stripped;
    }

    private TenantConfigResponse buildConfigResponse(Tenant tenant) {
        String activeApiKey = tenantApiKeyRepository.findByTenantId(tenant.getId())
            .stream()
            .filter(k -> Boolean.TRUE.equals(k.getIsActive()))
            .map(k -> k.getApiKey())
            .findFirst()
            .orElse(null);

        String tenantType = MASTER_TENANT_ID.equals(tenant.getId()) ? "MASTER" : "TENANT";

        // BUG 2 FIX: read live kill-switch and platform mode from tenant/service
        boolean killSwitchActive = Boolean.TRUE.equals(tenant.getKillSwitchActive());
        String  platformMode;
        try {
            platformMode = platformModeService.getMode(tenant.getId()).name();
        } catch (Exception e) {
            platformMode = "NORMAL";
        }

        TenantConfigResponse.FeaturesDto features = TenantConfigResponse.FeaturesDto.builder()
            .registrationOpen(Boolean.TRUE.equals(tenant.getRegistrationOpen()))
            .kycRequired(Boolean.TRUE.equals(tenant.getKycRequired()))
            .demoEnabled(true)
            .defaultDemoBalance(tenant.getDemoBalance())
            .minDeposit(tenant.getMinDeposit())
            .maxDeposit(tenant.getMaxDeposit())
            .minWithdrawal(tenant.getMinWithdrawal())
            .maxWithdrawal(tenant.getMaxWithdrawal())
            .autoWithdrawalLimit(tenant.getAutoWithdrawalLimit())
            .maxConcurrentTrades(tenant.getMaxConcurrentTrades() != null
                ? tenant.getMaxConcurrentTrades() : 3)
            .build();

        return TenantConfigResponse.builder()
            .tenantId(tenant.getId())
            .brandName(tenant.getBrandName())
            .logoUrl(tenant.getLogoUrl())
            .faviconUrl(tenant.getFaviconUrl())
            .primaryColor(tenant.getPrimaryColor())
            .accentColor(tenant.getAccentColor())
            .supportEmail(tenant.getSupportEmail())
            .apiKey(activeApiKey)
            .tenantType(tenantType)
            .killSwitchActive(killSwitchActive)
            .platformMode(platformMode)
            .features(features)
            .build();
    }
}