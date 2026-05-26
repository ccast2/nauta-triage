package com.nauta.triage.persistence.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "decisions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DecisionEntity {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "container_id", nullable = false)
    private UUID containerId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    @Column(name = "path", nullable = false)
    private String path;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "confidence", nullable = false, precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(name = "reasoning", nullable = false)
    private String reasoning;

    @Column(name = "next_action", nullable = false)
    private String nextAction;

    @Type(JsonBinaryType.class)
    @Column(name = "inputs_snapshot_json", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> inputsSnapshotJson;

    @Type(JsonBinaryType.class)
    @Column(name = "rules_fired_json", columnDefinition = "jsonb")
    private Map<String, Object> rulesFiredJson;

    @Column(name = "llm_call_id")
    private UUID llmCallId;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    @Column(name = "superseded_by")
    private UUID supersededBy;
}
