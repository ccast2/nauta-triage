package com.nauta.triage.admin;

import com.nauta.triage.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class AdminTenantControllerIT extends AbstractPostgresIT {
    @LocalServerPort int port;

    @Test
    @SuppressWarnings("unchecked")
    void lists_seeded_tenants_with_demo_tokens() {
        var client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        List<Map<String, Object>> resp = client.get().uri("/admin/tenants").retrieve().body(List.class);
        assertThat(resp).isNotEmpty();
        var tokens = resp.stream().map(t -> t.get("demo_token")).toList();
        assertThat(tokens).contains("demo-token-a", "demo-token-b");
        assertThat(resp.get(0)).containsKeys("id", "name", "demo_token");
    }
}
