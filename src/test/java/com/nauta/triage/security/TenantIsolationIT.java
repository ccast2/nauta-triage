package com.nauta.triage.security;

import com.nauta.triage.AbstractPostgresIT;
import com.nauta.triage.persistence.entity.ContainerEntity;
import com.nauta.triage.persistence.entity.ContainerStateEntity;
import com.nauta.triage.persistence.entity.DecisionEntity;
import com.nauta.triage.persistence.entity.TenantEntity;
import com.nauta.triage.persistence.repository.ContainerRepository;
import com.nauta.triage.persistence.repository.ContainerStateRepository;
import com.nauta.triage.persistence.repository.DecisionRepository;
import com.nauta.triage.persistence.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TenantIsolationIT extends AbstractPostgresIT {

    @Autowired TenantRepository tenants;
    @Autowired ContainerRepository containers;
    @Autowired ContainerStateRepository statesRepo;
    @Autowired DecisionRepository decisionsRepo;
    @LocalServerPort int port;

    @Test
    void repository_query_cannot_cross_tenants() {
        TenantEntity a = tenants.save(TenantEntity.builder()
            .name("A").apiTokenHash("a").build());
        TenantEntity b = tenants.save(TenantEntity.builder()
            .name("B").apiTokenHash("b").build());

        ContainerEntity cA = containers.save(ContainerEntity.builder()
            .tenantId(a.getId()).containerBusinessId("MSCU1234567").build());
        ContainerEntity cB = containers.save(ContainerEntity.builder()
            .tenantId(b.getId()).containerBusinessId("MSCU1234567")  // same business id, different tenant
            .build());

        assertThat(containers.findByTenantIdAndContainerBusinessId(a.getId(), "MSCU1234567"))
            .get().extracting(ContainerEntity::getId).isEqualTo(cA.getId());

        assertThat(containers.findByTenantIdAndContainerBusinessId(b.getId(), "MSCU1234567"))
            .get().extracting(ContainerEntity::getId).isEqualTo(cB.getId());

        assertThat(containers.findByTenantIdAndContainerBusinessId(a.getId(), "MSCU_NOT_EXIST")).isEmpty();
    }

    @Test
    void tenant_A_token_cannot_read_tenant_B_container() {
        String tokA = "iso-tok-A-" + java.util.UUID.randomUUID();
        String tokB = "iso-tok-B-" + java.util.UUID.randomUUID();
        var a = tenants.save(TenantEntity.builder().name("isoA")
            .apiTokenHash(BearerAuthInterceptor.hash(tokA)).build());
        var b = tenants.save(TenantEntity.builder().name("isoB")
            .apiTokenHash(BearerAuthInterceptor.hash(tokB)).build());

        var cB = containers.save(ContainerEntity.builder().tenantId(b.getId())
            .containerBusinessId("MSCU-CROSS").build());
        var d = decisionsRepo.save(DecisionEntity.builder().containerId(cB.getId()).tenantId(b.getId())
            .decidedAt(Instant.now()).path("rule").status("ON_TRACK").confidence(new BigDecimal("0.910"))
            .reasoning("ok").nextAction("wait").inputsSnapshotJson(Map.of()).latencyMs(5).build());
        statesRepo.save(ContainerStateEntity.builder().containerId(cB.getId()).tenantId(b.getId())
            .status("ON_TRACK").confidence(new BigDecimal("0.910")).reasoning("ok").nextAction("wait")
            .decidedByPath("rule").lastDecisionId(d.getId()).lastSourceRefreshAt(Instant.now())
            .version(0).updatedAt(Instant.now()).build());

        var client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        var resp = client.get().uri("/containers/MSCU-CROSS/status")
            .header("Authorization", "Bearer " + tokA)
            .retrieve()
            .onStatus(s -> true, (req, res) -> {})
            .toBodilessEntity();
        assertThat(resp.getStatusCode().value()).isEqualTo(404);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = client.get().uri("/containers/MSCU-CROSS/status")
            .header("Authorization", "Bearer " + tokB)
            .retrieve()
            .body(Map.class);
        assertThat(body).containsEntry("status", "ON_TRACK");
    }
}
