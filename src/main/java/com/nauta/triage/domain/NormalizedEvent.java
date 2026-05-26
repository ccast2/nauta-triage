package com.nauta.triage.domain;

import lombok.Builder;
import lombok.Value;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Value @Builder
public class NormalizedEvent {
    UUID sourceId;
    String sourceName;
    String containerBusinessId;
    EventType type;
    Instant timestamp;
    Map<String, Object> extra;
}
