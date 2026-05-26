package com.nauta.triage.security;

public class TenantNotResolvedException extends RuntimeException {
    public TenantNotResolvedException() {
        super("No tenant resolved for current request");
    }
}
