package com.nauta.triage.persistence.repository;

import com.nauta.triage.persistence.entity.DecisionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DecisionRepository extends JpaRepository<DecisionEntity, UUID> {
    List<DecisionEntity> findAllByTenantIdAndContainerIdOrderByDecidedAtDesc(UUID tenantId, UUID containerId);
}
