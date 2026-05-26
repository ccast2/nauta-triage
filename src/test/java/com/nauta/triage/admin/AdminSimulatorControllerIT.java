package com.nauta.triage.admin;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nauta.triage.AbstractPostgresIT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

class AdminSimulatorControllerIT extends AbstractPostgresIT {
    static WireMockServer wm;

    @LocalServerPort int port;

    @BeforeAll
    static void startWiremock() {
        wm = new WireMockServer(options().dynamicPort());
        wm.start();
    }

    @AfterAll
    static void stopWiremock() { if (wm != null) wm.stop(); }

    @DynamicPropertySource
    static void registerSim(DynamicPropertyRegistry r) {
        r.add("triage.simulators.fake-sim.base-url", () -> "http://localhost:" + wm.port());
        r.add("triage.simulators.fake-sim.url-template", () -> "/containers/{containerId}");
    }

    @Test
    @SuppressWarnings("unchecked")
    void upsert_then_list_then_delete_against_real_wiremock() {
        var client = RestClient.builder().baseUrl("http://localhost:" + port).build();

        // upsert
        Map<String, Object> stub = client.put().uri("/admin/simulators/fake-sim/stubs/MSCU-WM")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(Map.of("status", 200, "body", Map.of("eta", "2026-07-01")))
                .retrieve().body(Map.class);
        assertThat(stub).containsEntry("container_id", "MSCU-WM");

        // list
        List<Map<String, Object>> list = client.get().uri("/admin/simulators/fake-sim/stubs")
                .retrieve().body(List.class);
        assertThat(list).anyMatch(s -> "MSCU-WM".equals(s.get("container_id")));

        // verify wiremock actually serves the new stub
        var raw = RestClient.create().get().uri("http://localhost:" + wm.port() + "/containers/MSCU-WM")
                .retrieve().body(Map.class);
        assertThat(raw).containsEntry("eta", "2026-07-01");

        // delete
        var del = client.delete().uri("/admin/simulators/fake-sim/stubs/MSCU-WM")
                .retrieve().toBodilessEntity();
        assertThat(del.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void unknown_simulator_returns_404() {
        var client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        var res = client.get().uri("/admin/simulators/nope/stubs")
                .retrieve().onStatus(s -> true, (req, rsp) -> {}).toBodilessEntity();
        assertThat(res.getStatusCode().value()).isEqualTo(404);
    }
}
