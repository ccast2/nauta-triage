package com.nauta.triage.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record TenantDto(UUID id, String name, @JsonProperty("demo_token") String demoToken) {}
