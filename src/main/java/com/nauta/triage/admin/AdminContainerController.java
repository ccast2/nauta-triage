package com.nauta.triage.admin;

import com.nauta.triage.admin.dto.*;
import com.nauta.triage.admin.error.AdminException;
import com.nauta.triage.persistence.entity.ContainerEntity;
import com.nauta.triage.persistence.entity.ContainerStateEntity;
import com.nauta.triage.persistence.entity.DecisionEntity;
import com.nauta.triage.persistence.entity.RawEventEntity;
import com.nauta.triage.persistence.repository.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/admin/containers")
public class AdminContainerController {
    private final ContainerRepository containers;
    private final ContainerStateRepository states;
    private final DecisionRepository decisions;
    private final RawEventRepository rawEvents;

    public AdminContainerController(ContainerRepository c, ContainerStateRepository s,
                                    DecisionRepository d, RawEventRepository r) {
        this.containers = c; this.states = s; this.decisions = d; this.rawEvents = r;
    }

    @GetMapping
    public List<ContainerSummaryDto> list(@RequestHeader("X-Tenant-Id") UUID tenantId) {
        var rows = containers.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
        var stateById = new HashMap<UUID, ContainerStateEntity>();
        if (!rows.isEmpty()) {
            states.findAllByTenantIdAndContainerIdIn(tenantId,
                    rows.stream().map(ContainerEntity::getId).toList())
                    .forEach(st -> stateById.put(st.getContainerId(), st));
        }
        return rows.stream().map(c -> toSummary(c, stateById.get(c.getId()))).toList();
    }

    @PostMapping
    public ResponseEntity<ContainerSummaryDto> create(@RequestHeader("X-Tenant-Id") UUID tenantId,
                                                      @Valid @RequestBody ContainerCreateRequest req) {
        if (containers.existsByTenantIdAndContainerBusinessId(tenantId, req.businessId())) {
            throw new AdminException(HttpStatus.CONFLICT, "duplicate_business_id",
                    "container business_id already exists for tenant");
        }
        var saved = containers.save(ContainerEntity.builder()
                .tenantId(tenantId)
                .containerBusinessId(req.businessId())
                .carrier(req.carrier())
                .pol(req.pol())
                .pod(req.pod())
                .declaredEta(req.declaredEta())
                .build());
        return ResponseEntity.status(HttpStatus.CREATED).body(toSummary(saved, null));
    }

    @GetMapping("/{businessId}")
    public ContainerDetailDto get(@RequestHeader("X-Tenant-Id") UUID tenantId,
                                  @PathVariable String businessId) {
        var c = mustFind(tenantId, businessId);
        var state = states.findByTenantIdAndContainerId(tenantId, c.getId()).orElse(null);
        var decisionList = decisions.findAllByTenantIdAndContainerIdOrderByDecidedAtDesc(tenantId, c.getId())
                .stream().map(this::decisionToMap).toList();
        var rawList = rawEvents.findAllByTenantIdAndContainerBusinessIdOrderByReceivedAtDesc(tenantId, businessId)
                .stream().map(this::rawEventToMap).toList();
        Map<String, Object> stateMap = state == null ? null : Map.of(
                "status", state.getStatus(),
                "confidence", state.getConfidence(),
                "reasoning", state.getReasoning(),
                "next_action", state.getNextAction(),
                "decided_by_path", state.getDecidedByPath(),
                "updated_at", state.getUpdatedAt());
        return new ContainerDetailDto(toSummary(c, state), stateMap, decisionList, rawList);
    }

    @PatchMapping("/{businessId}")
    public ContainerSummaryDto patch(@RequestHeader("X-Tenant-Id") UUID tenantId,
                                     @PathVariable String businessId,
                                     @RequestBody ContainerPatchRequest req) {
        var c = mustFind(tenantId, businessId);
        if (req.carrier() != null) c.setCarrier(req.carrier());
        if (req.pol() != null) c.setPol(req.pol());
        if (req.pod() != null) c.setPod(req.pod());
        if (req.declaredEta() != null) c.setDeclaredEta(req.declaredEta());
        var saved = containers.save(c);
        var state = states.findByTenantIdAndContainerId(tenantId, saved.getId()).orElse(null);
        return toSummary(saved, state);
    }

    @DeleteMapping("/{businessId}")
    public ResponseEntity<Void> delete(@RequestHeader("X-Tenant-Id") UUID tenantId,
                                       @PathVariable String businessId) {
        var c = mustFind(tenantId, businessId);
        containers.delete(c);
        return ResponseEntity.noContent().build();
    }

    private ContainerEntity mustFind(UUID tenantId, String businessId) {
        return containers.findByTenantIdAndContainerBusinessId(tenantId, businessId)
                .orElseThrow(() -> new AdminException(HttpStatus.NOT_FOUND, "container_not_found",
                        "container " + businessId + " not found for tenant"));
    }

    private ContainerSummaryDto toSummary(ContainerEntity c, ContainerStateEntity s) {
        return new ContainerSummaryDto(
                c.getId(), c.getContainerBusinessId(), c.getCarrier(), c.getPol(), c.getPod(),
                c.getDeclaredEta(),
                s == null ? null : s.getStatus(),
                s == null ? null : s.getConfidence(),
                s == null ? null : s.getNextAction(),
                s == null ? c.getUpdatedAt() : s.getUpdatedAt());
    }

    private Map<String, Object> decisionToMap(DecisionEntity d) {
        var m = new LinkedHashMap<String, Object>();
        m.put("id", d.getId());
        m.put("decided_at", d.getDecidedAt());
        m.put("path", d.getPath());
        m.put("status", d.getStatus());
        m.put("confidence", d.getConfidence());
        m.put("reasoning", d.getReasoning());
        m.put("next_action", d.getNextAction());
        m.put("inputs_snapshot", d.getInputsSnapshotJson());
        m.put("latency_ms", d.getLatencyMs());
        return m;
    }

    private Map<String, Object> rawEventToMap(RawEventEntity e) {
        var m = new LinkedHashMap<String, Object>();
        m.put("id", e.getId());
        m.put("source_id", e.getSourceId());
        m.put("received_at", e.getReceivedAt());
        m.put("processed_at", e.getProcessedAt());
        m.put("processing_status", e.getProcessingStatus());
        m.put("payload", e.getPayloadJson());
        return m;
    }
}
