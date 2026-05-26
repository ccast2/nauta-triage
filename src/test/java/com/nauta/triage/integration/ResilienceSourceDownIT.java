package com.nauta.triage.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.nauta.triage.AbstractPostgresIT;
import com.nauta.triage.persistence.entity.*;
import com.nauta.triage.persistence.repository.*;
import com.nauta.triage.reconciliation.EventNormalizer;
import com.nauta.triage.reconciliation.ReconciliationService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Map;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

class ResilienceSourceDownIT extends AbstractPostgresIT {

    static WireMockServer healthy;
    static WireMockServer flaky;

    @Autowired TenantRepository tenants;
    @Autowired SourceRepository sources;
    @Autowired ContainerRepository containers;
    @Autowired RawEventRepository raw;
    @Autowired DecisionRepository decisions;
    @Autowired EventNormalizer normalizer;
    @Autowired ReconciliationService reconciliation;

    @BeforeAll
    static void up() {
        healthy = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        flaky = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        healthy.start();
        flaky.start();
    }
    @AfterAll static void down() { healthy.stop(); flaky.stop(); }

    @Test
    @SuppressWarnings("unchecked")
    void degrades_gracefully_when_one_source_is_down() {
        var t = tenants.save(TenantEntity.builder().name("res-t").apiTokenHash("res-h").build());
        var sH = sources.save(SourceEntity.builder().name("res-healthy").connectorType("carrier-portal-v1")
            .configJson(Map.of("base_url", healthy.baseUrl()))
            .mappingJson(stdMapping()).enabled(true).supportsWebhook(false).build());
        sources.save(SourceEntity.builder().name("res-flaky").connectorType("terminal-feed-v1")
            .configJson(Map.of("base_url", flaky.baseUrl()))
            .mappingJson(stdMapping()).enabled(true).supportsWebhook(false).build());

        flaky.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(503)));

        var c = containers.save(ContainerEntity.builder().tenantId(t.getId())
            .containerBusinessId("MSCU-RES").declaredEta(LocalDate.now().plusDays(20)).build());

        var rawEv = raw.save(RawEventEntity.builder().tenantId(t.getId()).sourceId(sH.getId())
            .containerBusinessId("MSCU-RES")
            .payloadJson(Map.of("container_id","MSCU-RES","events", List.of(
                Map.of("type","sail","date", Instant.now().minusSeconds(3600).toString()))))
            .receivedAt(Instant.now()).processingStatus("pending").processingAttempts(0).build());

        normalizer.normalize(rawEv);
        reconciliation.reconcile(c.getId());

        var ds = decisions.findAllByTenantIdAndContainerIdOrderByDecidedAtDesc(t.getId(), c.getId());
        assertThat(ds).isNotEmpty();
        var d = ds.get(0);

        Map<String, Object> snap = (Map<String, Object>) d.getInputsSnapshotJson();
        assertThat(snap).containsKey("sources");
        List<Map<String,Object>> srcList = (List<Map<String,Object>>) snap.get("sources");
        assertThat(srcList).anySatisfy(s -> {
            assertThat(s.get("name")).isEqualTo("res-flaky");
            assertThat(s.get("responded")).isEqualTo(false);
        });
        assertThat(d.getConfidence()).isLessThan(new BigDecimal("0.80"));
    }

    private Map<String, Object> stdMapping() {
        return Map.of(
            "container_id_path","$.container_id",
            "events_path","$.events",
            "event_type_path","$.type",
            "event_timestamp_path","$.date");
    }
}
