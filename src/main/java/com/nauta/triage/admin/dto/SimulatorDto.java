package com.nauta.triage.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SimulatorDto(
        @JsonProperty("source_name") String sourceName,
        @JsonProperty("base_url") String baseUrl,
        boolean reachable) {}
