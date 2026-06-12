package com.lezifx.trading.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lezifx.trading.infrastructure.context.TenantContext;
import com.lezifx.trading.repository.TenantApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@Order(1)
public class ApiKeyResolutionFilter extends OncePerRequestFilter {

    private static final List<String> SKIP_PATHS = List.of(
            "/api/v1/public/",
            "/api/v1/mpesa/c2b/callback",
            "/actuator/",
            "/ws/"
    );

    private static final String CACHE_PREFIX = "apikey:tenant:";
    private static final long   CACHE_TTL_MINUTES = 5;

    private final TenantApiKeyRepository    tenantApiKeyRepository;
    private final ObjectMapper              objectMapper;
    private final RedisTemplate<String, String> redisTemplate;

    @Autowired
    public ApiKeyResolutionFilter(@Lazy TenantApiKeyRepository tenantApiKeyRepository,
                                  ObjectMapper objectMapper,
                                  RedisTemplate<String, String> redisTemplate) {
        this.tenantApiKeyRepository = tenantApiKeyRepository;
        this.objectMapper           = objectMapper;
        this.redisTemplate          = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();

        for (String skip : SKIP_PATHS) {
            if (path.startsWith(skip)) {
                chain.doFilter(request, response);
                return;
            }
        }

        String apiKey = request.getHeader("X-API-Key");
        if (apiKey == null || apiKey.isBlank()) {
            writeError(response, HttpStatus.UNAUTHORIZED, "MISSING_API_KEY",
                    "X-API-Key header is required");
            return;
        }

        UUID tenantId = resolveTenantId(apiKey);

        if (tenantId == null) {
            log.warn("Invalid API key for path: {} key prefix: {}",
                    path, apiKey.length() > 12 ? apiKey.substring(0, 12) + "..." : apiKey);
            writeError(response, HttpStatus.UNAUTHORIZED, "INVALID_API_KEY",
                    "The provided API key is invalid or has been revoked");
            return;
        }

        TenantContext.set(tenantId);
        log.debug("Tenant resolved: path={} tenantId={}", path, tenantId);

        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private UUID resolveTenantId(String apiKey) {
        String cacheKey = CACHE_PREFIX + apiKey;

        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return UUID.fromString(cached);
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for API key lookup, falling back to DB: {}", e.getMessage());
        }

        var tenantApiKey = tenantApiKeyRepository
                .findByApiKeyAndIsActiveTrue(apiKey).orElse(null);

        if (tenantApiKey == null) return null;

        UUID tenantId = tenantApiKey.getTenant().getId();

        try {
            redisTemplate.opsForValue().set(cacheKey, tenantId.toString(),
                    CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Redis write failed for API key cache: {}", e.getMessage());
        }

        return tenantId;
    }

    private void writeError(HttpServletResponse response, HttpStatus status,
                            String error, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                Map.of("error", error, "message", message));
    }
}
