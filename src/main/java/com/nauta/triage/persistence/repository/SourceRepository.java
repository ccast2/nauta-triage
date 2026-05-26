package com.nauta.triage.persistence.repository;

import com.nauta.triage.persistence.entity.SourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SourceRepository extends JpaRepository<SourceEntity, UUID> {
    List<SourceEntity> findAllByEnabledTrue();
    List<SourceEntity> findAllByEnabledTrueAndSupportsWebhookTrue();
    List<SourceEntity> findAllByEnabledTrueAndPollingIntervalSecIsNotNull();
    Optional<SourceEntity> findByName(String name);
}
