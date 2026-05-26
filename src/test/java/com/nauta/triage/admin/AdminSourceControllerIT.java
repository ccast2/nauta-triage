package com.nauta.triage.admin;

import com.nauta.triage.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class AdminSourceControllerIT extends AbstractPostgresIT {
    @LocalServerPort int port;

    @Test
    @SuppressWarnings("unchecked")
    void crud_and_subscriptions_roundtrip() {
        var client = RestClient.builder().baseUrl("http://localhost:" + port).build();

        var sourceName = "src-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> created = client.post().uri("/admin/sources")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "name", sourceName,
                        "connector_type", "carrier_portal",
                        "polling_interval_sec", 30,
                        "supports_webhook", false,
                        "enabled", true,
                        "config_json", Map.of("base_url", "http://example/", "subscriptions", List.of()),
                        "mapping_json", Map.of("event_root", "$.events")
                )).retrieve().body(Map.class);
        assertThat(created).containsEntry("name", sourceName);
        var id = created.get("id").toString();

        Map<String, Object> sub = client.post().uri("/admin/sources/" + id + "/subscriptions")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(Map.of("tenant_id", UUID.randomUUID().toString(),
                        "container_business_id", "MSCU-SUB1"))
                .retrieve().body(Map.class);
        assertThat((List<?>) sub.get("subscriptions")).hasSize(1);

        // duplicate add is idempotent
        Map<String, Object> sub2 = client.post().uri("/admin/sources/" + id + "/subscriptions")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(Map.of("tenant_id", ((Map<?, ?>) ((List<?>) sub.get("subscriptions")).get(0)).get("tenant_id"),
                        "container_business_id", "MSCU-SUB1"))
                .retrieve().body(Map.class);
        assertThat((List<?>) sub2.get("subscriptions")).hasSize(1);

        var del = client.delete().uri("/admin/sources/" + id)
                .retrieve().toBodilessEntity();
        assertThat(del.getStatusCode().value()).isEqualTo(204);
    }
}
