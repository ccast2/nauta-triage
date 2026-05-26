package com.nauta.triage.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "container_states")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContainerStateEntity {
    @Id
    @Column(name = "container_id", nullable = false)
    private UUID containerId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "confidence", nullable = false, precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(name = "reasoning", nullable = false)
    private String reasoning;

    @Column(name = "next_action", nullable = false)
    private String nextAction;

    @Column(name = "reconciled_eta")
    private LocalDate reconciledEta;

    @Column(name = "reconciled_etd")
    private LocalDate reconciledEtd;

    @Column(name = "decided_by_path", nullable = false)
    private String decidedByPath;

    @Column(name = "last_decision_id", nullable = false)
    private UUID lastDecisionId;

    @Column(name = "last_source_refresh_at", nullable = false)
    private Instant lastSourceRefreshAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
