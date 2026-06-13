package com.lezifx.trading.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lezifx.trading.infrastructure.context.TenantContext;
import com.lezifx.trading.repository.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces the kill switch on the server side.
 * Runs AFTER JwtAuthFilter (Order 3) so SecurityContextHolder is already populated.
 *
 * When a tenant's kill_switch_active = true:
 *  - SUPER_ADMIN requests are allowed through (they need to manage the tenant)
 *  - all other authenticated/player requests receive HTTP 503 PLATFORM_OFFLINE
 *
 * Public, auth, actuator, websocket, and superadmin paths are never blocked.
 * Kill-switch state is cached per tenant for CACHE_TTL_MS to avoid a DB hit per request.
 */
@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class KillSwitchFilter extends OncePerRequestFilter {

    private static final long CACHE_TTL_MS = 10_000L; // 10 seconds

    private static final List<String> SKIP_PREFIXES = List.of(
            "/api/v1/public/",
            "/api/v1/auth/",
            "/api/v1/mpesa/",
            "/api/v1/superadmin/",
            "/actuator/",
            "/ws/"
    );

    private final TenantRepository tenantRepository;
    private final ObjectMapper     objectMapper;

    // Simple in-process cache: tenantId → [killSwitchActive, loadedAtMs]
    private final ConcurrentHashMap<UUID, long[]> cache = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         chain) throws ServletException, IOException {

        String path = request.getRequestURI();

        for (String prefix : SKIP_PREFIXES) {
            if (path.startsWith(prefix)) {
                chain.doFilter(request, response);
                return;
            }
        }

        UUID tenantId = TenantContext.getOrNull();
        if (tenantId == null) {
            // No tenant resolved (public/ws paths that skip ApiKeyResolutionFilter)
            chain.doFilter(request, response);
            return;
        }

        // SuperAdmin bypasses kill switch enforcement on all paths
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()))) {
            chain.doFilter(request, response);
            return;
        }

        if (isKillSwitchActive(tenantId)) {
            log.debug("Kill switch active for tenant {} — rejecting {} {}", tenantId, request.getMethod(), path);
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), Map.of(
                    "code",    "PLATFORM_OFFLINE",
                    "message", "The platform is currently offline for maintenance. Your balance is safe."
            ));
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isKillSwitchActive(UUID tenantId) {
        long now = System.currentTimeMillis();
        long[] entry = cache.get(tenantId);

        if (entry != null && now - entry[1] < CACHE_TTL_MS) {
            return entry[0] == 1L;
        }

        // Cache miss or stale — reload from DB
        try {
            boolean active = tenantRepository.findById(tenantId)
                    .map(t -> Boolean.TRUE.equals(t.getKillSwitchActive()))
                    .orElse(false);

            cache.put(tenantId, new long[]{ active ? 1L : 0L, now });
            return active;
        } catch (Exception e) {
            log.warn("KillSwitchFilter: DB lookup failed for tenant {}, allowing through: {}", tenantId, e.getMessage());
            return false;
        }
    }

    /**
     * Called by AdminPlatformService after a kill-switch state change to invalidate
     * the in-process cache immediately (rather than waiting for the 10-second TTL).
     */
    public void invalidateCache(UUID tenantId) {
        cache.remove(tenantId);
        log.debug("KillSwitchFilter: cache invalidated for tenant {}", tenantId);
    }
}
