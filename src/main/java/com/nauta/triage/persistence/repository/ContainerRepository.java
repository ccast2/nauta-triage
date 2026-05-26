package com.nauta.triage.persistence.repository;

import com.nauta.triage.persistence.entity.ContainerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContainerRepository extends JpaRepository<ContainerEntity, UUID> {
    Optional<ContainerEntity> findByTenantIdAndContainerBusinessId(UUID tenantId, String containerBusinessId);
    Optional<ContainerEntity> findByTenantIdAndId(UUID tenantId, UUID id);
    List<ContainerEntity> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    boolean existsByTenantIdAndContainerBusinessId(UUID tenantId, String containerBusinessId);
}
