package com.nauta.triage.sources.mapping;

import com.nauta.triage.domain.EventType;
import com.nauta.triage.domain.NormalizedEvent;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class MappingEngineTest {

    private final MappingEngine engine = new MappingEngine();

    private final Map<String, Object> standardMapping = Map.of(
        "container_id_path", "$.container_id",
        "events_path", "$.events",
        "event_type_path", "$.type",
        "event_type_map", Map.of("departure", "sail", "arrived", "arrival"),
        "event_timestamp_path", "$.date",
        "eta_path", "$.eta",
        "note_path", "$.note"
    );

    @Test
    void maps_carrier_portal_payload_to_canonical_events() {
        Map<String, Object> payload = Map.of(
            "container_id", "MSCU1234567",
            "events", List.of(
                Map.of("type", "gate_out", "date", "2026-04-28T10:32:00Z"),
                Map.of("type", "departure", "date", "2026-04-30T04:15:00Z")
            ),
            "eta", "2026-05-20",
            "note", "Vessel delayed at LISBOA, ETA may shift."
        );

        UUID srcId = UUID.randomUUID();
        List<NormalizedEvent> events = engine.normalize(payload, standardMapping, srcId, "carrier-portal");

        assertThat(events).hasSize(2);
        assertThat(events.get(0).getType()).isEqualTo(EventType.gate_out);
        assertThat(events.get(1).getType()).isEqualTo(EventType.sail);
        assertThat(events.get(0).getContainerBusinessId()).isEqualTo("MSCU1234567");
        assertThat(events.get(0).getExtra()).containsKey("note");
    }

    @Test
    void throws_on_missing_required_path() {
        Map<String, Object> payload = Map.of("foo", "bar");
        assertThatThrownBy(() -> engine.normalize(payload, standardMapping, UUID.randomUUID(), "x"))
            .isInstanceOf(MappingError.class);
    }

    @Test
    void unknown_event_type_is_skipped_not_thrown() {
        Map<String, Object> payload = Map.of(
            "container_id", "X",
            "events", List.of(
                Map.of("type", "weird_type", "date", "2026-04-28T10:32:00Z"),
                Map.of("type", "arrived", "date", "2026-04-29T10:32:00Z")
            )
        );
        List<NormalizedEvent> events = engine.normalize(payload, standardMapping, UUID.randomUUID(), "x");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getType()).isEqualTo(EventType.arrival);
    }
}
