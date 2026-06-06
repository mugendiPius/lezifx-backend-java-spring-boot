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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    private final TenantApiKeyRepository tenantApiKeyRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public ApiKeyResolutionFilter(@Lazy TenantApiKeyRepository tenantApiKeyRepository,
                                  ObjectMapper objectMapper) {
        this.tenantApiKeyRepository = tenantApiKeyRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();

        for (String skip : SKIP_PATHS) {
            if (path.startsWith(skip)) {
                log.debug("Skipping API key check for path: {}", path);
                chain.doFilter(request, response);
                return;
            }
        }

        String apiKey = request.getHeader("X-API-Key");
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Missing X-API-Key header for path: {}", path);
            writeError(response, HttpStatus.UNAUTHORIZED, "MISSING_API_KEY",
                    "X-API-Key header is required");
            return;
        }

        var tenantApiKey = tenantApiKeyRepository
                .findByApiKeyAndIsActiveTrue(apiKey).orElse(null);

        if (tenantApiKey == null) {
            log.warn("Invalid API key for path: {} key prefix: {}",
                    path, apiKey.length() > 12 ? apiKey.substring(0, 12) + "..." : apiKey);
            writeError(response, HttpStatus.UNAUTHORIZED, "INVALID_API_KEY",
                    "The provided API key is invalid or has been revoked");
            return;
        }

        UUID tenantId = tenantApiKey.getTenant().getId();
        TenantContext.set(tenantId);
        log.info("Tenant resolved: path={} tenantId={}", path, tenantId);

        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void writeError(HttpServletResponse response, HttpStatus status,
                            String error, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                Map.of("error", error, "message", message));
    }
}