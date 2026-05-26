package com.nauta.triage.api;

import com.nauta.triage.api.dto.ContainerStatusResponse;
import com.nauta.triage.api.dto.DecisionResponse;
import com.nauta.triage.persistence.entity.ContainerStateEntity;
import com.nauta.triage.persistence.entity.DecisionEntity;
import com.nauta.triage.persistence.repository.ContainerRepository;
import com.nauta.triage.persistence.repository.ContainerStateRepository;
import com.nauta.triage.persistence.repository.DecisionRepository;
import com.nauta.triage.security.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/containers")
public class ContainerStatusController {
    private final ContainerRepository containers;
    private final ContainerStateRepository states;
    private final DecisionRepository decisions;
    private final RefreshOrchestrator refresh;
    private final long ttlMinutes;
    private final long refreshBudgetMs = 1000;

    public ContainerStatusController(ContainerRepository c, ContainerStateRepository s, DecisionRepository d,
                                     RefreshOrchestrator r,
                                     @Value("${triage.default-source-freshness-ttl-minutes:15}") long ttl) {
        this.containers = c; this.states = s; this.decisions = d; this.refresh = r; this.ttlMinutes = ttl;
    }

    @GetMapping("/{businessId}/status")
    public ResponseEntity<ContainerStatusResponse> status(@PathVariable String businessId) {
        var tenantId = TenantContext.currentTenantId();
        var container = containers.findByTenantIdAndContainerBusinessId(tenantId, businessId).orElse(null);
        if (container == null) return ResponseEntity.notFound().build();

        var state = states.findByTenantIdAndContainerId(tenantId, container.getId()).orElse(null);
        if (state == null) return ResponseEntity.status(404).build();

        if (Duration.between(state.getLastSourceRefreshAt(), Instant.now()).toMinutes() >= ttlMinutes) {
            refresh.refreshWithin(Duration.ofMillis(refreshBudgetMs), tenantId, businessId);
            state = states.findByTenantIdAndContainerId(tenantId, container.getId()).orElse(state);
        }

        return ResponseEntity.ok(toDto(container.getContainerBusinessId(), state));
    }

    @GetMapping("/{businessId}/decisions")
    public ResponseEntity<List<DecisionResponse>> decisions(@PathVariable String businessId) {
        var tenantId = TenantContext.currentTenantId();
        var container = containers.findByTenantIdAndContainerBusinessId(tenantId, businessId).orElse(null);
        if (container == null) return ResponseEntity.notFound().build();
        var list = decisions.findAllByTenantIdAndContainerIdOrderByDecidedAtDesc(tenantId, container.getId());
        return ResponseEntity.ok(list.stream().map(this::toDecisionDto).toList());
    }

    private ContainerStatusResponse toDto(String businessId, ContainerStateEntity s) {
        return ContainerStatusResponse.builder()
            .containerId(businessId)
            .status(s.getStatus())
            .confidence(s.getConfidence())
            .reasoning(s.getReasoning())
            .nextAction(s.getNextAction())
            .reconciledEta(s.getReconciledEta())
            .reconciledEtd(s.getReconciledEtd())
            .decidedAt(s.getUpdatedAt())
            .decidedByPath(s.getDecidedByPath())
            .build();
    }

    private DecisionResponse toDecisionDto(DecisionEntity d) {
        return DecisionResponse.builder()
            .id(d.getId())
            .decidedAt(d.getDecidedAt())
            .path(d.getPath())
            .status(d.getStatus())
            .confidence(d.getConfidence())
            .reasoning(d.getReasoning())
            .nextAction(d.getNextAction())
            .inputsSnapshot(d.getInputsSnapshotJson())
            .latencyMs(d.getLatencyMs())
            .build();
    }
}
