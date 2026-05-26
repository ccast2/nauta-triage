package com.nauta.triage.reconciliation;

import com.nauta.triage.domain.DecisionPath;
import com.nauta.triage.domain.EventType;
import com.nauta.triage.domain.NormalizedEvent;
import com.nauta.triage.reconciliation.rules.SourceSnapshot;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class ConfidenceCalibratorTest {
    private final ConfidenceCalibrator c = new ConfidenceCalibrator();
    private final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void rule_path_with_full_coverage_full_agreement_and_fresh_data_is_high() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var snap = List.of(
            SourceSnapshot.builder().sourceId(a).sourceName("a").responded(true)
                .events(List.of(NormalizedEvent.builder().sourceId(a).type(EventType.sail).timestamp(NOW.minusSeconds(3600)).extra(Map.of()).containerBusinessId("c").sourceName("a").build())).build(),
            SourceSnapshot.builder().sourceId(b).sourceName("b").responded(true)
                .events(List.of(NormalizedEvent.builder().sourceId(b).type(EventType.sail).timestamp(NOW.minusSeconds(3600)).extra(Map.of()).containerBusinessId("c").sourceName("b").build())).build()
        );
        double conf = c.calibrate(0.92, DecisionPath.rule, snap, NOW);
        assertThat(conf).isGreaterThan(0.85);
    }

    @Test
    void fallback_with_one_responder_caps_low() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var snap = List.of(
            SourceSnapshot.builder().sourceId(a).sourceName("a").responded(true)
                .events(List.of(NormalizedEvent.builder().sourceId(a).type(EventType.sail).timestamp(NOW.minusSeconds(86400L * 8)).extra(Map.of()).containerBusinessId("c").sourceName("a").build())).build(),
            SourceSnapshot.builder().sourceId(b).sourceName("b").responded(false).events(List.of()).build()
        );
        double conf = c.calibrate(0.40, DecisionPath.fallback, snap, NOW);
        assertThat(conf).isLessThan(0.45);
    }
}
