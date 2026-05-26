package com.nauta.triage.ingestion;

import com.nauta.triage.domain.RawEventPayload;
import com.nauta.triage.persistence.entity.RawEventEntity;
import com.nauta.triage.persistence.repository.RawEventRepository;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.UUID;

@Service
public class IngestionService {
    private final RawEventRepository raw;
    public IngestionService(RawEventRepository raw) { this.raw = raw; }

    public UUID ingest(UUID tenantId, UUID sourceId, RawEventPayload payload) {
        RawEventEntity e = RawEventEntity.builder()
            .tenantId(tenantId)
            .sourceId(sourceId)
            .containerBusinessId(payload.getContainerBusinessId())
            .payloadJson(payload.getRaw())
            .receivedAt(Instant.now())
            .processingStatus("pending")
            .processingAttempts(0)
            .build();
        return raw.save(e).getId();
    }
}
