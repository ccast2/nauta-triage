package com.nauta.triage.reconciliation;

import com.nauta.triage.persistence.entity.ContainerEntity;
import com.nauta.triage.persistence.repository.ContainerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class ContainerResolver {
    private final ContainerRepository repo;
    public ContainerResolver(ContainerRepository r) { this.repo = r; }

    @Transactional
    public ContainerEntity resolveOrCreate(UUID tenantId, String businessId) {
        return repo.findByTenantIdAndContainerBusinessId(tenantId, businessId)
            .orElseGet(() -> repo.save(ContainerEntity.builder()
                .tenantId(tenantId).containerBusinessId(businessId).build()));
    }
}
