package com.nauta.triage.ingestion;

import com.nauta.triage.AbstractPostgresIT;
import com.nauta.triage.persistence.entity.SourceEntity;
import com.nauta.triage.persistence.entity.TenantEntity;
import com.nauta.triage.persistence.repository.RawEventRepository;
import com.nauta.triage.persistence.repository.SourceRepository;
import com.nauta.triage.persistence.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class WebhookControllerIT extends AbstractPostgresIT {
    @LocalServerPort int port;
    @Autowired TenantRepository tenants;
    @Autowired SourceRepository sources;
    @Autowired RawEventRepository raw;

    @Test
    void accepts_valid_signed_webhook_and_persists_raw_event() throws Exception {
        var t = tenants.save(TenantEntity.builder().name("wh-tenant").apiTokenHash("wh-h1").build());
        var s = sources.save(SourceEntity.builder()
            .name("partner-webhook-it").connectorType("partner-webhook-v1")
            .configJson(Map.of())
            .mappingJson(Map.of("container_id_path","$.container_id","events_path","$.events","event_type_path","$.type","event_timestamp_path","$.date"))
            .enabled(true).supportsWebhook(true).webhookSecret("s3cret").build());

        String body = "{\"container_id\":\"MSCU-WEB1\",\"events\":[{\"type\":\"sail\",\"date\":\"2026-04-01T00:00:00Z\"}]}";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("s3cret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String sig = HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));

        var client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        var resp = client.post()
            .uri("/webhooks/{tid}/sources/partner-webhook-it", t.getId())
            .header("X-Signature", sig)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .body(body)
            .retrieve()
            .toBodilessEntity();
        assertThat(resp.getStatusCode().value()).isEqualTo(202);
        assertThat(raw.findAllByTenantIdAndContainerBusinessIdOrderByReceivedAtDesc(t.getId(), "MSCU-WEB1")).hasSize(1);
    }

    @Test
    void rejects_bad_signature() {
        var t = tenants.save(TenantEntity.builder().name("wh-bad").apiTokenHash("wh-bad").build());
        var s = sources.save(SourceEntity.builder()
            .name("partner-webhook-bad").connectorType("partner-webhook-v1")
            .configJson(Map.of()).mappingJson(Map.of())
            .enabled(true).supportsWebhook(true).webhookSecret("right-secret").build());

        var client = RestClient.builder().baseUrl("http://localhost:" + port).build();
        var resp = client.post()
            .uri("/webhooks/{tid}/sources/partner-webhook-bad", t.getId())
            .header("X-Signature", "deadbeef")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .body("{}")
            .retrieve()
            .onStatus(s2 -> true, (req, res) -> {})
            .toBodilessEntity();
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }
}
