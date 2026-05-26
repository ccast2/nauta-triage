package com.nauta.triage.reconciliation;

import com.nauta.triage.AbstractPostgresIT;
import com.nauta.triage.persistence.entity.*;
import com.nauta.triage.persistence.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class EventNormalizerIT extends AbstractPostgresIT {
    @Autowired EventNormalizer normalizer;
    @Autowired TenantRepository tenants;
    @Autowired SourceRepository sources;
    @Autowired RawEventRepository raw;

    @Test
    void normalizes_carrier_payload_and_writes_canonical_events() {
        var t = tenants.save(TenantEntity.builder().name("norm-t").apiTokenHash("norm-h").build());
        var s = sources.save(SourceEntity.builder()
            .name("norm-carrier-portal").connectorType("carrier-portal-v1")
            .configJson(Map.of())
            .mappingJson(Map.of(
                "container_id_path","$.container_id",
                "events_path","$.events",
                "event_type_path","$.type",
                "event_timestamp_path","$.date",
                "event_type_map", Map.of("departure","sail"),
                "eta_path","$.eta"))
            .enabled(true).supportsWebhook(false).build());

        var rawEvent = raw.save(RawEventEntity.builder()
            .tenantId(t.getId()).sourceId(s.getId())
            .containerBusinessId("MSCU-NORM1")
            .payloadJson(Map.of(
                "container_id","MSCU-NORM1",
                "events", List.of(
                    Map.of("type","gate_out","date","2026-04-01T00:00:00Z"),
                    Map.of("type","departure","date","2026-04-02T00:00:00Z")),
                "eta","2026-05-01"))
            .receivedAt(Instant.now()).processingStatus("pending").processingAttempts(0).build());

        var out = normalizer.normalize(rawEvent);
        assertThat(out).hasSize(2);
        assertThat(out).extracting(NormalizedEventEntity::getEventType).containsExactly("gate_out","sail");
    }
}
