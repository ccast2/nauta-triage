package com.nauta.triage.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record SourcePatchRequest(
        Boolean enabled,
        @JsonProperty("polling_interval_sec") Integer pollingIntervalSec,
        @JsonProperty("supports_webhook") Boolean supportsWebhook,
        @JsonProperty("config_json") Map<String, Object> configJson,
        @JsonProperty("mapping_json") Map<String, Object> mappingJson) {}
