package com.nauta.triage.reconciliation;

import com.nauta.triage.persistence.entity.RawEventEntity;
import com.nauta.triage.persistence.repository.RawEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;

@Component
@ConditionalOnProperty(name = "triage.reconciliation.worker-enabled", havingValue = "true", matchIfMissing = true)
public class ReconciliationWorker {
    private static final Logger log = LoggerFactory.getLogger(ReconciliationWorker.class);

    private final RawEventRepository raw;
    private final EventNormalizer normalizer;
    private final ReconciliationService reconciliation;
    private final int batchSize;
    private final TransactionTemplate txTemplate;

    public ReconciliationWorker(RawEventRepository r, EventNormalizer n, ReconciliationService s,
                                @Value("${triage.reconciliation.worker-batch-size:25}") int batchSize,
                                PlatformTransactionManager txManager) {
        this.raw = r; this.normalizer = n; this.reconciliation = s; this.batchSize = batchSize;
        this.txTemplate = new TransactionTemplate(txManager);
    }

    @Scheduled(fixedDelayString = "${triage.reconciliation.worker-poll-interval-ms:500}")
    public void tick() {
        var batch = txTemplate.execute(s -> raw.claimBatchSystemWide(batchSize));
        if (batch == null || batch.isEmpty()) return;
        for (var ev : batch) {
            try {
                txTemplate.executeWithoutResult(s -> processOne(ev));
            } catch (Exception e) {
                // already handled inside processOne
            }
        }
    }

    private void processOne(RawEventEntity ev) {
        try {
            var normalizedList = normalizer.normalize(ev);
            if (!normalizedList.isEmpty()) {
                reconciliation.reconcile(normalizedList.get(0).getContainerId());
            }
            ev.setProcessedAt(Instant.now());
            ev.setProcessingStatus("processed");
            raw.save(ev);
        } catch (Exception e) {
            log.warn("Reconciliation failed for raw_event {}: {}", ev.getId(), e.toString());
            int newAttempts = ev.getProcessingAttempts() + 1;
            ev.setProcessingAttempts(newAttempts);
            ev.setProcessingStatus(newAttempts >= 3 ? "failed_processing" : "pending");
            ev.setLastError(e.toString());
            raw.save(ev);
        }
    }
}
