package com.nauta.triage.sources;

import lombok.Value;
import java.util.Map;
import java.util.UUID;

@Value
public class SourceConfig {
    UUID sourceId;
    String name;
    Map<String, Object> configJson;
    Map<String, Object> mappingJson;
    String webhookSecret;
}
