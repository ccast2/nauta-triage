package com.nauta.triage.integration;

import com.nauta.triage.AbstractPostgresIT;
import com.nauta.triage.persistence.entity.*;
import com.nauta.triage.persistence.repository.*;
import com.nauta.triage.security.BearerAuthInterceptor;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

@TestPropertySource(properties = "triage.reconciliation.worker-enabled=true")
class EndToEndHappyPathIT extends AbstractPostgresIT {

    @LocalServerPort int port;
    @Autowired TenantRepository tenants;
    @Autowired SourceRepository sources;
    @Autowired ContainerRepository containers;
    @Autowired RawEventRepository raw;

    @Test
    void full_pipeline_yields_ON_TRACK_state_visible_via_API() {
        String tok = "e2e-" + UUID.randomUUID();
        var t = tenants.save(TenantEntity.builder().name("e2e-t").apiTokenHash(BearerAuthInterceptor.hash(tok)).build());
        var s1 = sources.save(stdSource("e2e-s1"));
        var s2 = sources.save(stdSource("e2e-s2"));
        containers.save(ContainerEntity.builder().tenantId(t.getId())
            .containerBusinessId("MSCU-E2E").declaredEta(LocalDate.now().plusDays(20)).build());

        raw.save(rawEv(t.getId(), s1.getId(), Instant.now().minusSeconds(3600)));
        raw.save(rawEv(t.getId(), s2.getId(), Instant.now().minusSeconds(3500)));

        var client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        Awaitility.await().atMost(20, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = client.get().uri("/containers/MSCU-E2E/status")
                .header("Authorization", "Bearer " + tok)
                .retrieve()
                .onStatus(s -> s.is4xxClientError(), (req, res) -> {})
                .body(Map.class);
            assertThat(resp).isNotNull();
            assertThat(resp.get("status")).isEqualTo("ON_TRACK");
            assertThat(((Number) resp.get("confidence")).doubleValue()).isGreaterThan(0.80);
        });
    }

    private SourceEntity stdSource(String name) {
        return SourceEntity.builder().name(name).connectorType("carrier-portal-v1")
            .configJson(Map.of()).mappingJson(Map.of(
                "container_id_path","$.container_id","events_path","$.events",
                "event_type_path","$.type","event_timestamp_path","$.date"))
            .enabled(true).supportsWebhook(false).build();
    }
    private RawEventEntity rawEv(UUID t, UUID s, Instant ts) {
        return RawEventEntity.builder().tenantId(t).sourceId(s).containerBusinessId("MSCU-E2E")
            .payloadJson(Map.of("container_id","MSCU-E2E","events",
                List.of(Map.of("type","sail","date", ts.toString()))))
            .receivedAt(Instant.now()).processingStatus("pending").processingAttempts(0).build();
    }
}
