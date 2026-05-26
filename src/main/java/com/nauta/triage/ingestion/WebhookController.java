package com.nauta.triage.ingestion;

import com.nauta.triage.persistence.entity.SourceEntity;
import com.nauta.triage.persistence.repository.SourceRepository;
import com.nauta.triage.sources.ConnectorRegistry;
import com.nauta.triage.sources.ContainerSourceConnector;
import com.nauta.triage.sources.SourceConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
public class WebhookController {

    private final SourceRepository sources;
    private final ConnectorRegistry registry;
    private final IngestionService ingestion;

    public WebhookController(SourceRepository s, ConnectorRegistry r, IngestionService i) {
        this.sources = s; this.registry = r; this.ingestion = i;
    }

    @PostMapping("/webhooks/{tenantId}/sources/{sourceName}")
    public ResponseEntity<Void> webhook(@PathVariable UUID tenantId,
                                        @PathVariable String sourceName,
                                        @RequestHeader(value = "X-Signature", required = false) String sig,
                                        @RequestBody byte[] body) {
        SourceEntity source = sources.findByName(sourceName).orElse(null);
        if (source == null || !source.isEnabled() || !source.isSupportsWebhook()) return ResponseEntity.notFound().build();
        if (!HmacValidator.valid(body, sig, source.getWebhookSecret())) return ResponseEntity.status(401).build();

        ContainerSourceConnector connector = registry.forType(source.getConnectorType());
        SourceConfig cfg = new SourceConfig(source.getId(), source.getName(), source.getConfigJson(),
            source.getMappingJson(), source.getWebhookSecret());

        var payloads = connector.parseWebhook(body, cfg);
        payloads.forEach(p -> ingestion.ingest(tenantId, source.getId(), p));
        return ResponseEntity.accepted().build();
    }
}
