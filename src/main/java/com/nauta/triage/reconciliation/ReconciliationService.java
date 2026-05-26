package com.nauta.triage.reconciliation;

import com.nauta.triage.domain.*;
import com.nauta.triage.persistence.entity.*;
import com.nauta.triage.persistence.repository.*;
import com.nauta.triage.reconciliation.rules.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class ReconciliationService {
    private final ContainerRepository containers;
    private final NormalizedEventRepository normalized;
    private final SourceRepository sources;
    private final ContainerStateRepository states;
    private final DecisionRepository decisions;
    private final RuleRouter router;
    private final ConfidenceCalibrator calibrator;
    private final TriageLLMClient llm;
    private final NextActionMapper nextActions;
    private final DomainEventPublisher events;

    public ReconciliationService(ContainerRepository c, NormalizedEventRepository n, SourceRepository s,
                                 ContainerStateRepository st, DecisionRepository d,
                                 RuleRouter r, ConfidenceCalibrator cc, TriageLLMClient l,
                                 NextActionMapper na, DomainEventPublisher e) {
        this.containers = c; this.normalized = n; this.sources = s; this.states = st; this.decisions = d;
        this.router = r; this.calibrator = cc; this.llm = l; this.nextActions = na; this.events = e;
    }

    @Transactional
    public DecisionEntity reconcile(UUID containerId) {
        long t0 = System.currentTimeMillis();
        ContainerEntity container = containers.findById(containerId).orElseThrow();
        List<SourceEntity> enabledSources = sources.findAllByEnabledTrue();

        List<SourceSnapshot> snapshots = buildSnapshots(containerId, enabledSources);
        RuleDecision rd = router.evaluate(snapshots, container.getDeclaredEta(), Instant.now());

        ContainerStatus status;
        double baseConf;
        String reasoning;
        DecisionPath path;
        UUID llmCallId = null;
        List<String> rules = rd.getRulesFired();

        if (rd.isDecided()) {
            status = rd.getStatus();
            baseConf = rd.getBaseConfidence();
            reasoning = rd.getReasoning();
            path = DecisionPath.rule;
        } else {
            var llmResult = llm.triage(snapshots, container.getDeclaredEta(), rd.getInconclusiveReason());
            if (llmResult.isOk()) {
                status = llmResult.getStatus();
                baseConf = llmResult.getSelfReportedConfidence();
                reasoning = "LLM: " + llmResult.getReasoning();
                path = DecisionPath.llm;
                llmCallId = llmResult.getLlmCallId();
            } else {
                status = ContainerStatus.NEEDS_REVIEW;
                baseConf = 0.40;
                reasoning = "Fallback: rules inconclusive (" + rd.getInconclusiveReason() + ") and LLM unavailable.";
                path = DecisionPath.fallback;
            }
        }

        double coverage = (double) snapshots.stream().filter(SourceSnapshot::isResponded).count() / Math.max(1, snapshots.size());
        double calibrated = calibrator.calibrate(baseConf, path, snapshots, Instant.now());
        NextAction next = nextActions.map(status, calibrated, coverage);

        DecisionEntity decision = decisions.save(DecisionEntity.builder()
            .containerId(containerId).tenantId(container.getTenantId())
            .decidedAt(Instant.now()).path(path.name())
            .status(status.name()).confidence(BigDecimal.valueOf(calibrated).setScale(3, java.math.RoundingMode.HALF_UP))
            .reasoning(reasoning).nextAction(next.name())
            .inputsSnapshotJson(snapshotForAudit(snapshots))
            .rulesFiredJson(Map.of("rules", rules))
            .llmCallId(llmCallId)
            .latencyMs((int)(System.currentTimeMillis() - t0))
            .build());

        upsertState(container, decision, status, calibrated, reasoning, next, path);
        return decision;
    }

    private List<SourceSnapshot> buildSnapshots(UUID containerId, List<SourceEntity> enabled) {
        Instant since = Instant.now().minusSeconds(60L * 24 * 3600);
        var allEvents = normalized.findAllByContainerIdAndEventTimestampAfterOrderByEventTimestampAsc(containerId, since);
        Map<UUID, List<NormalizedEventEntity>> bySource = new HashMap<>();
        for (var e : allEvents) bySource.computeIfAbsent(e.getSourceId(), k -> new ArrayList<>()).add(e);

        List<SourceSnapshot> out = new ArrayList<>();
        for (var s : enabled) {
            var list = bySource.getOrDefault(s.getId(), List.of());
            String note = list.stream().findFirst()
                .map(NormalizedEventEntity::getExtraJson)
                .map(x -> x == null ? null : (String) x.get("note"))
                .orElse(null);
            out.add(SourceSnapshot.builder()
                .sourceId(s.getId()).sourceName(s.getName())
                .responded(!list.isEmpty())
                .events(list.stream().map(e -> NormalizedEvent.builder()
                    .sourceId(e.getSourceId()).sourceName(s.getName())
                    .containerBusinessId("")
                    .type(EventType.valueOf(e.getEventType()))
                    .timestamp(e.getEventTimestamp())
                    .extra(e.getExtraJson() == null ? Map.of() : e.getExtraJson())
                    .build()).toList())
                .responseNote(note)
                .declaredEta(null)
                .build());
        }
        return out;
    }

    private Map<String, Object> snapshotForAudit(List<SourceSnapshot> snapshots) {
        return Map.of("sources", snapshots.stream().map(s -> Map.of(
            "id", s.getSourceId().toString(),
            "name", s.getSourceName(),
            "responded", s.isResponded(),
            "event_count", s.getEvents().size(),
            "event_types", s.getEvents().stream().map(e -> e.getType().name()).toList()
        )).toList());
    }

    private void upsertState(ContainerEntity c, DecisionEntity d, ContainerStatus status,
                             double confidence, String reasoning, NextAction next, DecisionPath path) {
        var existing = states.findById(c.getId()).orElse(null);
        UUID prev = existing == null ? null : existing.getLastDecisionId();
        var entity = existing == null
            ? ContainerStateEntity.builder().containerId(c.getId()).tenantId(c.getTenantId()).version(0).build()
            : existing;
        entity.setStatus(status.name());
        entity.setConfidence(BigDecimal.valueOf(confidence).setScale(3, java.math.RoundingMode.HALF_UP));
        entity.setReasoning(reasoning);
        entity.setNextAction(next.name());
        entity.setDecidedByPath(path.name());
        entity.setLastDecisionId(d.getId());
        entity.setLastSourceRefreshAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        states.save(entity);

        if (prev != null) events.publishStateRevised(c.getId(), d.getId(), prev);
    }
}
