package com.nauta.triage.ingestion;

import com.nauta.triage.AbstractPostgresIT;
import com.nauta.triage.domain.RawEventPayload;
import com.nauta.triage.persistence.entity.SourceEntity;
import com.nauta.triage.persistence.entity.TenantEntity;
import com.nauta.triage.persistence.repository.RawEventRepository;
import com.nauta.triage.persistence.repository.SourceRepository;
import com.nauta.triage.persistence.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class IngestionServiceIT extends AbstractPostgresIT {
    @Autowired IngestionService ingestion;
    @Autowired RawEventRepository raw;
    @Autowired TenantRepository tenants;
    @Autowired SourceRepository sources;

    @Test
    void persists_raw_event_with_pending_status() {
        var t = tenants.save(TenantEntity.builder().name("T").apiTokenHash("h-ing").build());
        var s = sources.save(SourceEntity.builder().name("src-ing").connectorType("carrier-portal-v1")
            .configJson(Map.of()).mappingJson(Map.of()).enabled(true).supportsWebhook(false).build());
        UUID id = ingestion.ingest(t.getId(), s.getId(), new RawEventPayload("MSCU1-ing", Map.of("foo","bar")));
        var ev = raw.findById(id).orElseThrow();
        assertThat(ev.getProcessingStatus()).isEqualTo("pending");
        assertThat(ev.getContainerBusinessId()).isEqualTo("MSCU1-ing");
        assertThat(ev.getPayloadJson()).containsEntry("foo","bar");
    }
}
