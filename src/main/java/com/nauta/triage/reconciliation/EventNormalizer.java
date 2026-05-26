package com.nauta.triage.reconciliation;

import com.nauta.triage.domain.NormalizedEvent;
import com.nauta.triage.persistence.entity.*;
import com.nauta.triage.persistence.repository.*;
import com.nauta.triage.sources.mapping.MappingEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class EventNormalizer {
    private final SourceRepository sources;
    private final MappingEngine mapping;
    private final NormalizedEventRepository normalizedRepo;
    private final ContainerResolver resolver;

    public EventNormalizer(SourceRepository s, MappingEngine m, NormalizedEventRepository nr, ContainerResolver cr) {
        this.sources = s; this.mapping = m; this.normalizedRepo = nr; this.resolver = cr;
    }

    @Transactional
    public List<NormalizedEventEntity> normalize(RawEventEntity raw) {
        SourceEntity source = sources.findById(raw.getSourceId()).orElseThrow();
        ContainerEntity container = resolver.resolveOrCreate(raw.getTenantId(), raw.getContainerBusinessId());

        List<NormalizedEvent> mapped = mapping.normalize(raw.getPayloadJson(), source.getMappingJson(),
            source.getId(), source.getName());

        List<NormalizedEventEntity> saved = new ArrayList<>();
        for (NormalizedEvent ev : mapped) {
            saved.add(normalizedRepo.save(NormalizedEventEntity.builder()
                .rawEventId(raw.getId())
                .containerId(container.getId())
                .sourceId(ev.getSourceId())
                .eventType(ev.getType().name())
                .eventTimestamp(ev.getTimestamp())
                .extraJson(ev.getExtra())
                .normalizedAt(Instant.now())
                .build()));
        }
        return saved;
    }
}
