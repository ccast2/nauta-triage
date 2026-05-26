package com.nauta.triage.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record SourceCreateRequest(
        @NotBlank String name,
        @JsonProperty("connector_type") @NotBlank String connectorType,
        @JsonProperty("polling_interval_sec") Integer pollingIntervalSec,
        @JsonProperty("supports_webhook") boolean supportsWebhook,
        boolean enabled,
        @JsonProperty("config_json") Map<String, Object> configJson,
        @JsonProperty("mapping_json") Map<String, Object> mappingJson) {}
