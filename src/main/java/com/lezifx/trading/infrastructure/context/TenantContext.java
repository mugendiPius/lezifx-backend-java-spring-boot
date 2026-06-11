package com.lezifx.trading.infrastructure.context;

import java.util.UUID;

/**
 * ThreadLocal holder for the current tenant.
 * Set by ApiKeyResolutionFilter on every request.
 * Cleared in the filter finally block  never leaks between requests.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(UUID tenantId) {
        CURRENT.set(tenantId);
    }

    public static UUID get() {
        UUID id = CURRENT.get();
        if (id == null) {
            throw new IllegalStateException(
                "TenantContext not initialised  request missing X-API-Key header");
        }
        return id;
    }

    public static UUID getOrNull() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}