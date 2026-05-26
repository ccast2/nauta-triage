package com.nauta.triage.api;

import com.nauta.triage.ingestion.IngestionService;
import com.nauta.triage.persistence.repository.SourceRepository;
import com.nauta.triage.sources.ConnectorRegistry;
import com.nauta.triage.sources.SourceConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;

@Component
public class RefreshOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(RefreshOrchestrator.class);

    private final SourceRepository sources;
    private final ConnectorRegistry registry;
    private final IngestionService ingestion;
    private final ExecutorService exec = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "refresh-orchestrator");
        t.setDaemon(true);
        return t;
    });

    public RefreshOrchestrator(SourceRepository s, ConnectorRegistry r, IngestionService i) {
        this.sources = s; this.registry = r; this.ingestion = i;
    }

    @PreDestroy
    public void shutdown() { exec.shutdownNow(); }

    /** Best-effort: refresh from polling sources within the budget. */
    public void refreshWithin(Duration budget, UUID tenantId, String containerBusinessId) {
        var pollingSources = sources.findAllByEnabledTrueAndPollingIntervalSecIsNotNull();
        Future<?> future = exec.submit(() -> {
            for (var src : pollingSources) {
                try {
                    var connector = registry.forType(src.getConnectorType());
                    var cfg = new SourceConfig(src.getId(), src.getName(), src.getConfigJson(), src.getMappingJson(), src.getWebhookSecret());
                    connector.fetch(containerBusinessId, cfg).forEach(p -> ingestion.ingest(tenantId, src.getId(), p));
                } catch (Exception e) {
                    log.warn("Refresh from {} failed for {}: {}", src.getName(), containerBusinessId, e.toString());
                }
            }
            return null;
        });
        try {
            TimeLimiter.of(TimeLimiterConfig.custom().timeoutDuration(budget).build())
                .executeFutureSupplier(() -> (Future<Object>)(Future<?>) future);
        } catch (Exception ignored) {
            future.cancel(true);
        }
    }
}
