package com.nauta.triage.security;

import java.util.UUID;

public final class TenantContext {
    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID tenantId) {
        CURRENT.set(tenantId);
    }

    public static UUID currentTenantId() {
        UUID t = CURRENT.get();
        if (t == null) {
            throw new TenantNotResolvedException();
        }
        return t;
    }

    public static UUID currentTenantIdOrNull() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
