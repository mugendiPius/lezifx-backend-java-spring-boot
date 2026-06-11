package com.lezifx.trading.web.controller.public_;

import com.lezifx.trading.domain.tenant.Tenant;
import com.lezifx.trading.repository.TenantApiKeyRepository;
import com.lezifx.trading.repository.TenantRepository;
import com.lezifx.trading.service.platform.PlatformModeService;
import com.lezifx.trading.web.dto.response.TenantConfigResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicConfigController {

    private static final UUID MASTER_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final TenantRepository       tenantRepository;
    private final TenantApiKeyRepository tenantApiKeyRepository;
    private final PlatformModeService    platformModeService;

    /**
     * Bootstrap endpoint called by every frontend on load.
     * Resolution order:
     *   1. Extract domain from X-Domain header, then Origin, then Referer.
     *   2. Query allowed_origins array  GIN-indexed, very fast.
     *   3. Fall back to master tenant if no match found.
     *
     * This ensures that any registered deployment URL (Render, Vercel, custom)
     * resolves to the correct tenant, and unknown URLs fall back to master
     * rather than leaking cross-tenant data.
     */
    @GetMapping("/config")
    public TenantConfigResponse getConfig(HttpServletRequest request) {
        String domain = resolveDomain(request);
        log.debug("[PublicConfig] Resolving domain: {}", domain);

        Tenant tenant = null;

        if (domain != null && !domain.isBlank()) {
            tenant = tenantRepository.findByAllowedOriginsContaining(domain).orElse(null);
            if (tenant != null) {
                log.debug("[PublicConfig] Resolved domain '{}'  tenant '{}'", domain, tenant.getBrandName());
            }
        }

        if (tenant == null) {
            log.debug("[PublicConfig] Domain '{}' not found  falling back to master tenant", domain);
            tenant = tenantRepository.findById(MASTER_TENANT_ID)
                    .orElseThrow(() -> new RuntimeException("Master tenant not found"));
        }

        return buildConfigResponse(tenant);
    }

    //  Domain extraction 

    private String resolveDomain(HttpServletRequest request) {
        // 1. Explicit header  frontend can send this for clarity
        String xDomain = request.getHeader("X-Domain");
        if (xDomain != null && !xDomain.isBlank()) return normalise(xDomain.trim());

        // 2. Origin header  present on all CORS requests
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank()) return normalise(stripProtocol(origin));

        // 3. Referer header  fallback
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank()) return normalise(stripProtocol(referer));

        return null;
    }

    /** Strip https:// or http:// and trailing path. */
    private String stripProtocol(String url) {
        String stripped = url.replaceFirst("^https?://", "");
        int slashIdx = stripped.indexOf('/');
        return slashIdx > 0 ? stripped.substring(0, slashIdx) : stripped;
    }

    /** Lowercase and remove trailing slashes for consistent matching. */
    private String normalise(String domain) {
        return domain.toLowerCase().replaceAll("/+$", "");
    }

    //  Response builder 

    private TenantConfigResponse buildConfigResponse(Tenant tenant) {
        String activeApiKey = tenantApiKeyRepository.findByTenantId(tenant.getId())
                .stream()
                .filter(k -> Boolean.TRUE.equals(k.getIsActive()))
                .map(k -> k.getApiKey())
                .findFirst()
                .orElse(null);

        String tenantType = MASTER_TENANT_ID.equals(tenant.getId()) ? "MASTER" : "TENANT";

        boolean killSwitchActive = Boolean.TRUE.equals(tenant.getKillSwitchActive());

        String platformMode;
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