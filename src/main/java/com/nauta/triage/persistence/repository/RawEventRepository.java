package com.nauta.triage.persistence.repository;

import com.nauta.triage.persistence.entity.RawEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RawEventRepository extends JpaRepository<RawEventEntity, UUID> {

    @Query(value = """
        SELECT * FROM raw_events
        WHERE processing_status = 'pending'
        ORDER BY received_at ASC
        FOR UPDATE SKIP LOCKED
        LIMIT :limit
    """, nativeQuery = true)
    List<RawEventEntity> claimBatchSystemWide(@Param("limit") int limit);

    List<RawEventEntity> findAllByTenantIdAndContainerBusinessIdOrderByReceivedAtDesc(UUID tenantId, String containerBusinessId);
}
