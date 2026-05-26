package com.nauta.triage.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Value;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Value @Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class DecisionResponse {
    UUID id;
    Instant decidedAt;
    String path;
    String status;
    BigDecimal confidence;
    String reasoning;
    String nextAction;
    Map<String, Object> inputsSnapshot;
    int latencyMs;
}
