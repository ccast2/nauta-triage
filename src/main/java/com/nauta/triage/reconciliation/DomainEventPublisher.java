package com.nauta.triage.reconciliation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class DomainEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);
    public void publishStateRevised(UUID containerId, UUID newDecisionId, UUID previousDecisionId) {
        log.info("ContainerStateRevised container={} newDecision={} prev={}", containerId, newDecisionId, previousDecisionId);
    }
}
