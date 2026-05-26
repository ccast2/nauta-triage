package com.nauta.triage.admin;

import com.nauta.triage.AbstractPostgresIT;
import com.nauta.triage.persistence.entity.TenantEntity;
import com.nauta.triage.persistence.repository.TenantRepository;
import com.nauta.triage.security.BearerAuthInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class AdminContainerControllerIT extends AbstractPostgresIT {
    @LocalServerPort int port;
    @Autowired TenantRepository tenants;

    private RestClient client;
    private TenantEntity tenant;

    @BeforeEach
    void setup() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        tenant = tenants.save(TenantEntity.builder()
                .name("admin-test-" + UUID.randomUUID())
                .apiTokenHash(BearerAuthInterceptor.hash("tok-" + UUID.randomUUID()))
                .build());
    }

    @Test
    @SuppressWarnings("unchecked")
    void create_then_list_then_get_then_delete() {
        Map<String, Object> created = client.post().uri("/admin/containers")
                .header("X-Tenant-Id", tenant.getId().toString())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(Map.of("business_id", "MSCU-ADMIN-1", "carrier", "MSC",
                        "pol", "VES", "pod", "LIS", "declared_eta", "2026-08-01"))
                .retrieve().body(Map.class);
        assertThat(created).containsKeys("id", "business_id", "carrier");

        List<Map<String, Object>> list = client.get().uri("/admin/containers")
                .header("X-Tenant-Id", tenant.getId().toString())
                .retrieve().body(List.class);
        assertThat(list).hasSize(1);
        assertThat(list.get(0)).containsEntry("business_id", "MSCU-ADMIN-1");
        assertThat(list.get(0)).containsKeys("current_status", "current_confidence");

        Map<String, Object> detail = client.get().uri("/admin/containers/MSCU-ADMIN-1")
                .header("X-Tenant-Id", tenant.getId().toString())
                .retrieve().body(Map.class);
        assertThat(detail).containsKeys("container", "status", "decisions", "raw_events");

        var del = client.delete().uri("/admin/containers/MSCU-ADMIN-1")
                .header("X-Tenant-Id", tenant.getId().toString())
                .retrieve().toBodilessEntity();
        assertThat(del.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void duplicate_business_id_returns_409() {
        client.post().uri("/admin/containers")
                .header("X-Tenant-Id", tenant.getId().toString())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(Map.of("business_id", "MSCU-DUP")).retrieve().toBodilessEntity();
        var res = client.post().uri("/admin/containers")
                .header("X-Tenant-Id", tenant.getId().toString())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(Map.of("business_id", "MSCU-DUP"))
                .retrieve().onStatus(s -> true, (req, rsp) -> {}).toBodilessEntity();
        assertThat(res.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void missing_tenant_header_returns_400() {
        var res = client.get().uri("/admin/containers")
                .retrieve().onStatus(s -> true, (req, rsp) -> {}).toBodilessEntity();
        assertThat(res.getStatusCode().value()).isEqualTo(400);
    }
}
