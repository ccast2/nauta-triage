package com.nauta.triage.sources;

import lombok.Builder;
import lombok.Value;

@Value @Builder
public class SourceCapabilities {
    boolean supportsPolling;
    boolean supportsWebhook;
}
