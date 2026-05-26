package com.nauta.triage.api;

import com.nauta.triage.AbstractPostgresIT;
import com.nauta.triage.persistence.entity.*;
import com.nauta.triage.persistence.repository.*;
import com.nauta.triage.security.BearerAuthInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class ContainerStatusControllerIT extends AbstractPostgresIT {
    @LocalServerPort int port;
    @Autowired TenantRepository tenants;
    @Autowired ContainerRepository containers;
    @Autowired ContainerStateRepository states;
    @Autowired DecisionRepository decisions;

    @Test
    @SuppressWarnings("unchecked")
    void returns_state_for_authenticated_tenant() {
        String tok = "tkn-" + UUID.randomUUID();
        var t = tenants.save(TenantEntity.builder().name("api-acme")
            .apiTokenHash(BearerAuthInterceptor.hash(tok)).build());
        var c = containers.save(ContainerEntity.builder().tenantId(t.getId())
            .containerBusinessId("MSCU-API1").build());
        var d = decisions.save(DecisionEntity.builder().containerId(c.getId()).tenantId(t.getId())
            .decidedAt(Instant.now()).path("rule").status("ON_TRACK").confidence(new BigDecimal("0.910"))
            .reasoning("test").nextAction("wait").inputsSnapshotJson(Map.of()).latencyMs(10).build());
        states.save(ContainerStateEntity.builder().containerId(c.getId()).tenantId(t.getId())
            .status("ON_TRACK").confidence(new BigDecimal("0.910")).reasoning("ok").nextAction("wait")
            .decidedByPath("rule").lastDecisionId(d.getId()).lastSourceRefreshAt(Instant.now())
            .version(0).updatedAt(Instant.now()).build());

        var client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        Map<String, Object> resp = client.get().uri("/containers/MSCU-API1/status")
            .header("Authorization","Bearer " + tok).retrieve().body(Map.class);
        assertThat(resp).containsEntry("status","ON_TRACK");
        assertThat(resp).containsKey("confidence");
    }

    @Test
    void unauthenticated_request_returns_401() {
        var client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        var resp = client.get().uri("/containers/anything/status")
            .retrieve()
            .onStatus(s -> true, (req, res) -> {})
            .toBodilessEntity();
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }
}
