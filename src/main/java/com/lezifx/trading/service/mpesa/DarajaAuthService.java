package com.lezifx.trading.service.mpesa;

import com.lezifx.trading.domain.tenant.Tenant;
import com.lezifx.trading.repository.TenantRepository;
import com.lezifx.trading.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DarajaAuthService {

    private final TenantRepository tenantRepository;
    private final AesEncryptionService aesEncryptionService;
    private final RestTemplate restTemplate;

    @Value("${daraja.base-url:https://api.safaricom.co.ke}")
    private String defaultBaseUrl;

    @Cacheable(value = "darajaTokens", key = "#tenantId")
    public String getAccessToken(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND",
                "Tenant not found: " + tenantId));

        String consumerKey = tenant.getDarajaConsumerKey();
        String consumerSecret = tenant.getDarajaConsumerSecret();

        if (consumerKey == null || consumerKey.isBlank()) {
            throw new BusinessException("DARAJA_NOT_CONFIGURED",
                "M-Pesa not configured for this tenant");
        }

        String decryptedKey = aesEncryptionService.decrypt(consumerKey);
        String decryptedSecret = aesEncryptionService.decrypt(consumerSecret);

        String credentials = decryptedKey + ":" + decryptedSecret;
        String basicAuth = Base64.getEncoder()
            .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        // UriComponentsBuilder.fromHttpUrl() was removed in Spring 6.
        // Build the URL with simple string concatenation.
        String baseUrl = getBaseUrl(tenant);
        String url = baseUrl + "/oauth/v1/generate?grant_type=client_credentials";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + basicAuth);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, Map.class);
            Map<?, ?> body = response.getBody();
            if (body == null || !body.containsKey("access_token")) {
                throw new BusinessException("DARAJA_AUTH_FAILED",
                    "No access_token in Daraja OAuth response");
            }
            log.info("Daraja OAuth token refreshed for tenant {}", tenantId);
            return (String) body.get("access_token");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Daraja auth failed for tenant {}: {}", tenantId, e.getMessage());
            throw new BusinessException("DARAJA_AUTH_FAILED",
                "Failed to obtain Daraja access token: " + e.getMessage());
        }
    }

    public String getBaseUrl(Tenant tenant) {
        if ("SANDBOX".equalsIgnoreCase(tenant.getDarajaEnvironment())) {
            return "https://sandbox.safaricom.co.ke";
        }
        return defaultBaseUrl;
    }
}