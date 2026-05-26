package com.nauta.triage.reconciliation.rules;

import com.nauta.triage.domain.ContainerStatus;
import com.nauta.triage.domain.EventType;
import com.nauta.triage.domain.NormalizedEvent;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class RuleRouterTest {
    private final RuleRouter router = new RuleRouter();
    private final Instant NOW = Instant.parse("2026-05-26T12:00:00Z");

    private NormalizedEvent ev(UUID src, EventType type, String iso) {
        return NormalizedEvent.builder().sourceId(src).sourceName("s").containerBusinessId("c")
            .type(type).timestamp(Instant.parse(iso)).extra(Map.of()).build();
    }

    @Test
    void R2_two_sources_agree_on_sail_before_eta() {
        UUID s1 = UUID.randomUUID(), s2 = UUID.randomUUID();
        var snap = List.of(
            SourceSnapshot.builder().sourceId(s1).sourceName("a").responded(true)
                .events(List.of(ev(s1, EventType.gate_out, "2026-05-20T00:00:00Z"), ev(s1, EventType.sail, "2026-05-22T00:00:00Z"))).build(),
            SourceSnapshot.builder().sourceId(s2).sourceName("b").responded(true)
                .events(List.of(ev(s2, EventType.gate_out, "2026-05-20T00:01:00Z"), ev(s2, EventType.sail, "2026-05-22T00:30:00Z"))).build()
        );
        var d = router.evaluate(snap, LocalDate.parse("2026-06-10"), NOW);
        assertThat(d.isDecided()).isTrue();
        assertThat(d.getStatus()).isEqualTo(ContainerStatus.ON_TRACK);
        assertThat(d.getRulesFired()).contains("R2_ON_TRACK");
    }

    @Test
    void R3_past_eta_no_arrival_yields_DELAYED() {
        UUID s1 = UUID.randomUUID(), s2 = UUID.randomUUID();
        var snap = List.of(
            SourceSnapshot.builder().sourceId(s1).sourceName("a").responded(true)
                .events(List.of(ev(s1, EventType.sail, "2026-05-01T00:00:00Z"))).build(),
            SourceSnapshot.builder().sourceId(s2).sourceName("b").responded(true)
                .events(List.of(ev(s2, EventType.sail, "2026-05-01T00:30:00Z"))).build()
        );
        var d = router.evaluate(snap, LocalDate.parse("2026-05-20"), NOW);
        assertThat(d.getStatus()).isEqualTo(ContainerStatus.DELAYED);
        assertThat(d.getRulesFired()).contains("R3_DELAYED");
    }

    @Test
    void R5_conflict_over_tolerance_is_inconclusive() {
        UUID s1 = UUID.randomUUID(), s2 = UUID.randomUUID();
        var snap = List.of(
            SourceSnapshot.builder().sourceId(s1).sourceName("a").responded(true)
                .events(List.of(ev(s1, EventType.sail, "2026-05-01T00:00:00Z"))).build(),
            SourceSnapshot.builder().sourceId(s2).sourceName("b").responded(true)
                .events(List.of(ev(s2, EventType.sail, "2026-05-02T00:00:00Z"))).build()
        );
        var d = router.evaluate(snap, LocalDate.parse("2026-06-10"), NOW);
        assertThat(d.isDecided()).isFalse();
        assertThat(d.getInconclusiveReason()).isEqualTo("source_conflict_over_tolerance");
    }

    @Test
    void R6_one_of_two_sources_responded_is_inconclusive() {
        UUID s1 = UUID.randomUUID(), s2 = UUID.randomUUID();
        var snap = List.of(
            SourceSnapshot.builder().sourceId(s1).sourceName("a").responded(true)
                .events(List.of(ev(s1, EventType.sail, "2026-05-22T00:00:00Z"))).build(),
            SourceSnapshot.builder().sourceId(s2).sourceName("b").responded(false).events(List.of()).build()
        );
        var d = router.evaluate(snap, LocalDate.parse("2026-06-10"), NOW);
        assertThat(d.isDecided()).isFalse();
        assertThat(d.getInconclusiveReason()).isEqualTo("only_one_source_responded");
    }

    @Test
    void R1_stale_no_arrival_is_inconclusive() {
        UUID s1 = UUID.randomUUID();
        var snap = List.of(
            SourceSnapshot.builder().sourceId(s1).sourceName("a").responded(true)
                .events(List.of(ev(s1, EventType.sail, "2026-05-01T00:00:00Z"))).build()
        );
        var d = router.evaluate(snap, null, Instant.parse("2026-05-30T00:00:00Z"));
        assertThat(d.getInconclusiveReason()).isEqualTo("stale_no_arrival");
        assertThat(d.getRulesFired()).contains("R1_LOST_CANDIDATE");
    }

    @Test
    void R4_arrival_yields_ON_TRACK() {
        UUID s1 = UUID.randomUUID(), s2 = UUID.randomUUID();
        var snap = List.of(
            SourceSnapshot.builder().sourceId(s1).sourceName("a").responded(true)
                .events(List.of(ev(s1, EventType.sail,"2026-05-01T00:00:00Z"), ev(s1, EventType.arrival,"2026-05-19T00:00:00Z"))).build(),
            SourceSnapshot.builder().sourceId(s2).sourceName("b").responded(true)
                .events(List.of(ev(s2, EventType.arrival,"2026-05-19T01:00:00Z"))).build()
        );
        var d = router.evaluate(snap, LocalDate.parse("2026-05-20"), NOW);
        assertThat(d.getStatus()).isEqualTo(ContainerStatus.ON_TRACK);
    }

    @Test
    void R7_arrival_before_sail_in_same_source_is_inconclusive() {
        UUID s1 = UUID.randomUUID();
        var snap = List.of(
            SourceSnapshot.builder().sourceId(s1).sourceName("a").responded(true)
                .events(List.of(ev(s1, EventType.arrival,"2026-05-01T00:00:00Z"), ev(s1, EventType.sail,"2026-05-02T00:00:00Z"))).build()
        );
        var d = router.evaluate(snap, LocalDate.parse("2026-06-10"), NOW);
        assertThat(d.isDecided()).isFalse();
        assertThat(d.getInconclusiveReason()).isEqualTo("out_of_order_events");
    }
}
