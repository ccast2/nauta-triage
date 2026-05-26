package com.nauta.triage.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record SubscriptionRequest(
        @JsonProperty("tenant_id") UUID tenantId,
        @JsonProperty("container_business_id") @NotBlank String containerBusinessId) {}
