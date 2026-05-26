package com.nauta.triage.persistence.repository;

import com.nauta.triage.persistence.entity.NormalizedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NormalizedEventRepository extends JpaRepository<NormalizedEventEntity, UUID> {
    List<NormalizedEventEntity> findAllByContainerIdAndEventTimestampAfterOrderByEventTimestampAsc(UUID containerId, Instant since);
}
