package com.nauta.triage.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record StubDto(
        String id,
        @JsonProperty("container_id") String containerId,
        int status,
        Map<String, Object> body) {}
