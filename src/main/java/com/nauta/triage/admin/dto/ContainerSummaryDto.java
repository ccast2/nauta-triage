package com.nauta.triage.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ContainerSummaryDto(
        UUID id,
        @JsonProperty("business_id") String businessId,
        String carrier,
        String pol,
        String pod,
        @JsonProperty("declared_eta") LocalDate declaredEta,
        @JsonProperty("current_status") String currentStatus,
        @JsonProperty("current_confidence") BigDecimal currentConfidence,
        @JsonProperty("next_action") String nextAction,
        @JsonProperty("updated_at") Instant updatedAt) {}
