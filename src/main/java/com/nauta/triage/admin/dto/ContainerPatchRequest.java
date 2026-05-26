package com.nauta.triage.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

public record ContainerPatchRequest(
        String carrier,
        String pol,
        String pod,
        @JsonProperty("declared_eta") LocalDate declaredEta) {}
