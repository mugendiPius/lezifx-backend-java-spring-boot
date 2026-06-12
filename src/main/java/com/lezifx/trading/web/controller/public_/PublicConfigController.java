package com.lezifx.trading.web.controller.public_;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lezifx.trading.domain.tenant.Tenant;
import com.lezifx.trading.repository.TenantApiKeyRepository;
import com.lezifx.trading.repository.TenantRepository;
import com.lezifx.trading.service.platform.PlatformModeService;
import com.lezifx.trading.web.dto.response.TenantConfigResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicConfigController {

    private static final UUID   MASTER_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String CACHE_PREFIX      = "domain:config:";
    private static final long   CACHE_TTL_SECONDS = 30;

    private final TenantRepository              tenantRepository;
    private final TenantApiKeyRepository        tenantApiKeyRepository;
    private final PlatformModeService           platformModeService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper                  objectMapper;

    @GetMapping("/config")
    public TenantConfigResponse getConfig(HttpServletRequest request) {
        String domain = resolveDomain(request);
        log.debug("[PublicConfig] Resolving domain: {}", domain);

        Tenant tenant = null;

        if (domain != null && !domain.isBlank()) {
            try {
                String cached = redisTemplate.opsForValue().get(CACHE_PREFIX + domain);
                if (cached != null) {
                    log.debug("[PublicConfig] Cache hit for domain: {}", domain);
                    return objectMapper.readValue(cached, TenantConfigResponse.class);
                }
            } catch (Exception e) {
                log.warn("Redis unavailable for domain lookup, falling back to DB: {}", e.getMessage());
            }
            tenant = tenantRepository.findByAllowedOriginsContaining(domain).orElse(null);
            if (tenant != null) {
                log.debug("[PublicConfig] Resolved domain to tenant: {}", tenant.getBrandName());
            }
        }

        if (tenant == null) {
            log.debug("[PublicConfig] Falling back to master tenant");
            tenant = tenantRepository.findById(MASTER_TENANT_ID)
                    .orElseThrow(() -> new RuntimeException("Master tenant not found"));
        }

        TenantConfigResponse config = buildConfigResponse(tenant);

        if (domain != null && !domain.isBlank()) {
            try {
                redisTemplate.opsForValue().set(
                        CACHE_PREFIX + domain,
                        objectMapper.writeValueAsString(config),
                        CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Redis write failed for domain config cache: {}", e.getMessage());
            }
        }

        return config;
    }

    private String resolveDomain(HttpServletRequest request) {
        String xDomain = request.getHeader("X-Domain");
        if (xDomain != null && !xDomain.isBlank()) return normalise(xDomain.trim());
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank()) return normalise(stripProtocol(origin));
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank()) return normalise(stripProtocol(referer));
        return null;
    }

    private String stripProtocol(String url) {
        String stripped = url.replaceFirst("^https?://", "");
        int slashIdx = stripped.indexOf('/');
        return slashIdx > 0 ? stripped.substring(0, slashIdx) : stripped;
    }

    private String normalise(String domain) {
        return domain.toLowerCase().replaceAll("/+$", "");
    }

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