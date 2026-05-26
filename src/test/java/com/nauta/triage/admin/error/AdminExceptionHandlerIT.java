package com.nauta.triage.admin.error;

import com.nauta.triage.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class AdminExceptionHandlerIT extends AbstractPostgresIT {
    @LocalServerPort int port;

    @Test
    void unknown_admin_route_returns_envelope_404() {
        var client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        var resp = client.get().uri("/admin/does-not-exist")
                .retrieve()
                .onStatus(s -> true, (req, res) -> {})
                .toEntity(Map.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }
}
