package com.nauta.triage.integration;

import com.nauta.triage.AbstractPostgresIT;
import com.nauta.triage.persistence.entity.*;
import com.nauta.triage.persistence.repository.*;
import com.nauta.triage.reconciliation.EventNormalizer;
import com.nauta.triage.reconciliation.ReconciliationService;
import com.nauta.triage.reconciliation.TriageLLMClient;
import com.nauta.triage.reconciliation.rules.SourceSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@Import(LlmFallbackIT.FailingLlmConfig.class)
@TestPropertySource(properties = "triage.llm.enabled=true")
class LlmFallbackIT extends AbstractPostgresIT {

    @TestConfiguration
    static class FailingLlmConfig {
        @Bean @Primary
        TriageLLMClient failingLlm() {
            return (List<SourceSnapshot> s, LocalDate eta, String reason) ->
                new TriageLLMClient.LlmTriageResult(false, null, 0.0, "forced failure", null);
        }
    }

    @Autowired TenantRepository tenants;
    @Autowired SourceRepository sources;
    @Autowired ContainerRepository containers;
    @Autowired RawEventRepository raw;
    @Autowired DecisionRepository decisions;
    @Autowired EventNormalizer normalizer;
    @Autowired ReconciliationService reconciliation;

    @Test
    void fallback_emits_NEEDS_REVIEW_when_rules_inconclusive_and_LLM_fails() {
        var t = tenants.save(TenantEntity.builder().name("llm-t").apiTokenHash("llm-h").build());
        var s1 = sources.save(stdSource("llm-s1"));
        var s2 = sources.save(stdSource("llm-s2"));
        var c = containers.save(ContainerEntity.builder().tenantId(t.getId())
            .containerBusinessId("MSCU-LLM-FB").declaredEta(LocalDate.now().plusDays(20)).build());

        var r1 = raw.save(rawEv(t.getId(), s1.getId(), Instant.parse("2026-05-01T00:00:00Z")));
        var r2 = raw.save(rawEv(t.getId(), s2.getId(), Instant.parse("2026-05-02T00:00:00Z")));

        normalizer.normalize(r1);
        normalizer.normalize(r2);
        reconciliation.reconcile(c.getId());

        var ds = decisions.findAllByTenantIdAndContainerIdOrderByDecidedAtDesc(t.getId(), c.getId());
        assertThat(ds).isNotEmpty();
        var d = ds.get(0);
        assertThat(d.getStatus()).isEqualTo("NEEDS_REVIEW");
        assertThat(d.getPath()).isEqualTo("fallback");
        assertThat(d.getNextAction()).isEqualTo("escalate_to_human");
    }

    private SourceEntity stdSource(String name) {
        return SourceEntity.builder().name(name).connectorType("carrier-portal-v1")
            .configJson(Map.of()).mappingJson(Map.of(
                "container_id_path","$.container_id","events_path","$.events",
                "event_type_path","$.type","event_timestamp_path","$.date"))
            .enabled(true).supportsWebhook(false).build();
    }

    private RawEventEntity rawEv(UUID t, UUID s, Instant ts) {
        return RawEventEntity.builder().tenantId(t).sourceId(s).containerBusinessId("MSCU-LLM-FB")
            .payloadJson(Map.of("container_id","MSCU-LLM-FB","events",
                List.of(Map.of("type","sail","date", ts.toString()))))
            .receivedAt(Instant.now()).processingStatus("pending").processingAttempts(0).build();
    }
}
