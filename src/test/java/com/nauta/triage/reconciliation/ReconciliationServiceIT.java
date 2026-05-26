package com.nauta.triage.reconciliation;

import com.nauta.triage.AbstractPostgresIT;
import com.nauta.triage.persistence.entity.*;
import com.nauta.triage.persistence.repository.*;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

@TestPropertySource(properties = "triage.reconciliation.worker-enabled=true")
class ReconciliationServiceIT extends AbstractPostgresIT {
    @Autowired TenantRepository tenants;
    @Autowired SourceRepository sources;
    @Autowired ContainerRepository containers;
    @Autowired RawEventRepository raw;
    @Autowired ContainerStateRepository states;
    @Autowired DecisionRepository decisions;

    @Test
    void worker_processes_two_source_agreement_to_ON_TRACK() {
        var t = tenants.save(TenantEntity.builder().name("rec-t").apiTokenHash("rec-h").build());
        var s1 = mkSource("rec-a-portal");
        var s2 = mkSource("rec-b-terminal");
        var c = containers.save(ContainerEntity.builder().tenantId(t.getId())
            .containerBusinessId("MSCU-REC1").declaredEta(LocalDate.now().plusDays(20)).build());

        raw.save(buildRaw(t.getId(), s1.getId(), "MSCU-REC1",
            Map.of("container_id","MSCU-REC1","events", List.of(
                Map.of("type","sail","date", Instant.now().minusSeconds(3600).toString())))));
        raw.save(buildRaw(t.getId(), s2.getId(), "MSCU-REC1",
            Map.of("container_id","MSCU-REC1","events", List.of(
                Map.of("type","sail","date", Instant.now().minusSeconds(3500).toString())))));

        Awaitility.await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            var state = states.findByTenantIdAndContainerId(t.getId(), c.getId());
            assertThat(state).isPresent();
            assertThat(state.get().getStatus()).isEqualTo("ON_TRACK");
            assertThat(state.get().getConfidence().doubleValue()).isGreaterThan(0.80);
        });
    }

    private SourceEntity mkSource(String name) {
        return sources.save(SourceEntity.builder().name(name).connectorType("carrier-portal-v1")
            .configJson(Map.of()).mappingJson(Map.of(
                "container_id_path","$.container_id","events_path","$.events",
                "event_type_path","$.type","event_timestamp_path","$.date"))
            .enabled(true).supportsWebhook(false).build());
    }

    private RawEventEntity buildRaw(java.util.UUID t, java.util.UUID s, String c, Map<String,Object> payload) {
        return RawEventEntity.builder().tenantId(t).sourceId(s).containerBusinessId(c)
            .payloadJson(payload).receivedAt(Instant.now()).processingStatus("pending").processingAttempts(0).build();
    }
}
