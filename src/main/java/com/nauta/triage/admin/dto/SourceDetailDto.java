package com.nauta.triage.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.UUID;

public record SourceDetailDto(
        UUID id,
        String name,
        @JsonProperty("connector_type") String connectorType,
        boolean enabled,
        @JsonProperty("polling_interval_sec") Integer pollingIntervalSec,
        @JsonProperty("supports_webhook") boolean supportsWebhook,
        @JsonProperty("config_json") Map<String, Object> configJson,
        @JsonProperty("mapping_json") Map<String, Object> mappingJson) {}
