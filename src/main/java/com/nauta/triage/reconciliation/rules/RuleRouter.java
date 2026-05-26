package com.nauta.triage.reconciliation.rules;

import com.nauta.triage.domain.ContainerStatus;
import com.nauta.triage.domain.EventType;
import com.nauta.triage.domain.NormalizedEvent;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;

@Component
public class RuleRouter {

    private static final Duration STALE_LOST_WINDOW = Duration.ofDays(14);
    private static final Duration CONFLICT_TOLERANCE = Duration.ofHours(6);

    public RuleDecision evaluate(List<SourceSnapshot> snapshots, LocalDate declaredEta, Instant now) {
        List<String> fired = new ArrayList<>();
        var responded = snapshots.stream().filter(SourceSnapshot::isResponded).toList();
        if (responded.isEmpty()) return RuleDecision.inconclusive("no_sources_responded", fired);

        var allEvents = responded.stream().flatMap(s -> s.getEvents().stream()).toList();
        if (allEvents.isEmpty()) return RuleDecision.inconclusive("no_events", fired);

        Instant latest = allEvents.stream().map(NormalizedEvent::getTimestamp).max(Instant::compareTo).get();

        boolean anyArrival = allEvents.stream().anyMatch(e -> e.getType() == EventType.arrival);
        boolean anyDischarge = allEvents.stream().anyMatch(e -> e.getType() == EventType.discharge);

        // R7: out-of-order events
        if (hasOutOfOrderEvents(allEvents)) {
            fired.add("R7_OUT_OF_ORDER");
            return RuleDecision.inconclusive("out_of_order_events", fired);
        }

        // R5: timestamp conflict
        if (conflictExceedsTolerance(responded)) {
            fired.add("R5_CONFLICT");
            return RuleDecision.inconclusive("source_conflict_over_tolerance", fired);
        }

        // R4: arrival
        if (anyArrival) {
            fired.add("R4_ARRIVAL");
            return RuleDecision.decided(ContainerStatus.ON_TRACK, 0.90, "Arrival event observed across sources.", fired);
        }

        // R3: past ETA + no arrival
        if (declaredEta != null && now.atZone(ZoneOffset.UTC).toLocalDate().isAfter(declaredEta.plusDays(1))) {
            fired.add("R3_DELAYED");
            return RuleDecision.decided(ContainerStatus.DELAYED, 0.88,
                "Now is past ETA + 24h with no arrival event.", fired);
        }

        // R1: stale + no arrival
        if (Duration.between(latest, now).compareTo(STALE_LOST_WINDOW) > 0 && !anyArrival && !anyDischarge) {
            fired.add("R1_LOST_CANDIDATE");
            return RuleDecision.inconclusive("stale_no_arrival", fired);
        }

        // R6: insufficient coverage
        if (responded.size() == 1 && snapshots.size() > 1) {
            fired.add("R6_SPARSE_COVERAGE");
            return RuleDecision.inconclusive("only_one_source_responded", fired);
        }

        // R2: on-track happy path
        if (responded.size() >= 2 && !anyDischarge) {
            fired.add("R2_ON_TRACK");
            return RuleDecision.decided(ContainerStatus.ON_TRACK, 0.92,
                "Sources agree on event sequence; ETA not yet reached.", fired);
        }

        return RuleDecision.inconclusive("no_rule_matched", fired);
    }

    private boolean hasOutOfOrderEvents(List<NormalizedEvent> events) {
        Map<UUID, List<NormalizedEvent>> bySource = new HashMap<>();
        for (var e : events) bySource.computeIfAbsent(e.getSourceId(), k -> new ArrayList<>()).add(e);
        for (var list : bySource.values()) {
            list.sort(Comparator.comparing(NormalizedEvent::getTimestamp));
            int sailIdx = -1, arrivalIdx = -1;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getType() == EventType.sail && sailIdx == -1) sailIdx = i;
                if (list.get(i).getType() == EventType.arrival && arrivalIdx == -1) arrivalIdx = i;
            }
            if (sailIdx >= 0 && arrivalIdx >= 0 && arrivalIdx < sailIdx) return true;
        }
        return false;
    }

    private boolean conflictExceedsTolerance(List<SourceSnapshot> responded) {
        Map<EventType, List<Instant>> byType = new EnumMap<>(EventType.class);
        for (var s : responded) for (var e : s.getEvents())
            byType.computeIfAbsent(e.getType(), k -> new ArrayList<>()).add(e.getTimestamp());
        for (var list : byType.values()) {
            if (list.size() < 2) continue;
            Instant min = list.stream().min(Instant::compareTo).get();
            Instant max = list.stream().max(Instant::compareTo).get();
            if (Duration.between(min, max).compareTo(CONFLICT_TOLERANCE) > 0) return true;
        }
        return false;
    }
}
