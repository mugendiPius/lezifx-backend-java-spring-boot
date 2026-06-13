package com.lezifx.trading.config;

import com.lezifx.trading.repository.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Loads every allowed origin from the tenants.allowed_origins[] column and caches
 * them for 5 minutes. On each CORS preflight the incoming Origin is checked against
 * the cached set; if it matches the same origin is reflected back — satisfying
 * both multi-tenant requirements and allowCredentials:true semantics.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DynamicCorsConfigurationSource implements CorsConfigurationSource {

    private static final long CACHE_TTL_MS = 5 * 60 * 1_000L;

    private final TenantRepository tenantRepository;

    private final AtomicReference<Set<String>> cachedOrigins = new AtomicReference<>(Set.of());
    private final AtomicLong cacheLoadedAt = new AtomicLong(0L);

    @Override
    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) return null;

        Set<String> allowed = getAllowedOrigins();

        // Normalise: strip trailing slash for comparison
        String normalised = origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;

        if (!isAllowed(normalised, allowed)) {
            log.debug("CORS: rejected origin={}", normalised);
            return null;
        }

        CorsConfiguration cfg = new CorsConfiguration();
        cfg.addAllowedOrigin(normalised);   // reflect the exact matched origin
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of(
            "Authorization", "X-API-Key", "Content-Type", "X-Domain", "Origin",
            "Accept", "Cache-Control"
        ));
        cfg.setExposedHeaders(List.of("Authorization"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);
        return cfg;
    }

    private boolean isAllowed(String origin, Set<String> allowed) {
        if (allowed.contains("*")) return true;
        if (allowed.contains(origin)) return true;
        // Also match by host only (strip scheme) so "https://foo.com" matches stored "foo.com"
        try {
            String host = new java.net.URI(origin).getHost();
            if (host != null) {
                String withPort = origin.replaceFirst("https?://", "");
                return allowed.contains(host) || allowed.contains(withPort);
            }
        } catch (Exception ignored) {}
        return false;
    }

    private Set<String> getAllowedOrigins() {
        long now = System.currentTimeMillis();
        if (now - cacheLoadedAt.get() > CACHE_TTL_MS) {
            try {
                Set<String> fresh = tenantRepository.findAll()
                    .stream()
                    .filter(t -> t.getAllowedOrigins() != null)
                    .flatMap(t -> Arrays.stream(t.getAllowedOrigins()))
                    .filter(o -> o != null && !o.isBlank())
                    // Normalise stored values — add https:// prefix if no scheme present
                    .flatMap(o -> {
                        if (o.startsWith("http://") || o.startsWith("https://") || o.equals("*")) {
                            return java.util.stream.Stream.of(o);
                        }
                        // stored without scheme (e.g. "foo.onrender.com") — keep both the
                        // raw value AND the https-prefixed form so both match
                        return java.util.stream.Stream.of(o, "https://" + o, "http://" + o);
                    })
                    .collect(Collectors.toSet());

                cachedOrigins.set(fresh);
                cacheLoadedAt.set(now);
                log.debug("CORS cache refreshed — {} origins loaded", fresh.size());
            } catch (Exception e) {
                log.warn("CORS cache refresh failed, reusing stale set: {}", e.getMessage());
            }
        }
        return cachedOrigins.get();
    }
}
