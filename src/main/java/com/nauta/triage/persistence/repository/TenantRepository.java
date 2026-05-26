package com.nauta.triage.persistence.repository;

import com.nauta.triage.persistence.entity.TenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<TenantEntity, UUID> {
    Optional<TenantEntity> findByApiTokenHash(String apiTokenHash);
}
