package com.nauta.triage.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record ContainerCreateRequest(
        @JsonProperty("business_id") @NotBlank String businessId,
        String carrier,
        String pol,
        String pod,
        @JsonProperty("declared_eta") LocalDate declaredEta) {}
