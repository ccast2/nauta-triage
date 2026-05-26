package com.nauta.triage.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Value;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Value @Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ContainerStatusResponse {
    String containerId;
    String status;
    BigDecimal confidence;
    String reasoning;
    String nextAction;
    LocalDate reconciledEtd;
    LocalDate reconciledEta;
    Instant decidedAt;
    String decidedByPath;
}
