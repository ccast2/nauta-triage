package com.nauta.triage.persistence.repository;

import com.nauta.triage.persistence.entity.ContainerStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContainerStateRepository extends JpaRepository<ContainerStateEntity, UUID> {
    Optional<ContainerStateEntity> findByTenantIdAndContainerId(UUID tenantId, UUID containerId);
    List<ContainerStateEntity> findAllByTenantIdAndContainerIdIn(UUID tenantId, Collection<UUID> containerIds);
}
