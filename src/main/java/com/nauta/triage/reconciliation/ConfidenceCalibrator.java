package com.nauta.triage.reconciliation;

import com.nauta.triage.domain.DecisionPath;
import com.nauta.triage.reconciliation.rules.SourceSnapshot;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class ConfidenceCalibrator {

    private static final Duration FRESHNESS_WINDOW = Duration.ofDays(7);

    public double calibrate(double baseConfidence, DecisionPath path,
                            List<SourceSnapshot> snapshots, Instant now) {
        long responded = snapshots.stream().filter(SourceSnapshot::isResponded).count();
        if (responded == 0) return clamp(path == DecisionPath.fallback ? 0.40 : 0.20);

        double coverage = (double) responded / Math.max(1, snapshots.size());

        var allEvents = snapshots.stream()
            .filter(SourceSnapshot::isResponded)
            .flatMap(s -> s.getEvents().stream()).toList();
        double agreement = agreement(snapshots);
        double freshness = freshness(allEvents.isEmpty() ? now : allEvents.stream()
            .map(e -> e.getTimestamp()).max(Instant::compareTo).get(), now);

        double base = (path == DecisionPath.rule) ? Math.max(baseConfidence, 0.90) :
                      (path == DecisionPath.llm)  ? 0.70 * baseConfidence :
                                                    0.40;
        double calibrated = base * (0.5 + 0.25 * agreement + 0.15 * coverage + 0.10 * freshness);
        return clamp(calibrated);
    }

    private double agreement(List<SourceSnapshot> snapshots) {
        var responded = snapshots.stream().filter(SourceSnapshot::isResponded).toList();
        if (responded.size() < 2) return responded.isEmpty() ? 0.0 : 0.7;
        var modeSet = responded.get(0).getEvents().stream()
            .map(e -> e.getType().name()).sorted().toList();
        long matching = responded.stream().filter(s ->
            s.getEvents().stream().map(e -> e.getType().name()).sorted().toList().equals(modeSet)
        ).count();
        return (double) matching / responded.size();
    }

    private double freshness(Instant latest, Instant now) {
        double ratio = (double) Duration.between(latest, now).toMillis() / FRESHNESS_WINDOW.toMillis();
        return Math.max(0.0, Math.min(1.0, 1.0 - ratio));
    }

    private double clamp(double v) { return Math.max(0.0, Math.min(1.0, v)); }
}
