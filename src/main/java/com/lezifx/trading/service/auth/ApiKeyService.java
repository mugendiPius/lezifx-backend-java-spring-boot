package com.lezifx.trading.service.auth;

import com.lezifx.trading.domain.tenant.Tenant;
import com.lezifx.trading.domain.tenant.TenantApiKey;
import com.lezifx.trading.repository.TenantApiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final TenantApiKeyRepository tenantApiKeyRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generateApiKey(String type) {
        byte[] randomBytes = new byte[24];
        secureRandom.nextBytes(randomBytes);
        String random = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return "lzfx_" + type + "_" + random;
    }

    @Transactional
    public TenantApiKey createApiKey(Tenant tenant, String label) {
        TenantApiKey apiKey = TenantApiKey.builder()
            .tenant(tenant)
            .apiKey(generateApiKey("tenant"))
            .label(label)
            .isActive(true)
            .build();

        return tenantApiKeyRepository.save(apiKey);
    }

    @Transactional
    public void revokeApiKey(UUID apiKeyId) {
        TenantApiKey apiKey = tenantApiKeyRepository.findById(apiKeyId)
            .orElseThrow(() -> new com.lezifx.trading.web.exception.BusinessException(
                "API_KEY_NOT_FOUND", "API key not found"));

        apiKey.setIsActive(false);
        apiKey.setRevokedAt(Instant.now());
        tenantApiKeyRepository.save(apiKey);
    }
}