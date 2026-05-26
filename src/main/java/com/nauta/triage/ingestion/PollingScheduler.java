package com.nauta.triage.ingestion;

import com.nauta.triage.persistence.entity.SourceEntity;
import com.nauta.triage.persistence.repository.SourceRepository;
import com.nauta.triage.sources.ConnectorRegistry;
import com.nauta.triage.sources.ContainerSourceConnector;
import com.nauta.triage.sources.SourceConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class PollingScheduler {
    private static final Logger log = LoggerFactory.getLogger(PollingScheduler.class);

    private final SourceRepository sources;
    private final ConnectorRegistry registry;
    private final IngestionService ingestion;
    private final CircuitBreakerRegistry breakers;
    private final ExecutorService exec = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "polling-scheduler-" + threadIdx.incrementAndGet());
        t.setDaemon(true);
        return t;
    });
    private static final AtomicInteger threadIdx = new AtomicInteger();

    @PreDestroy
    public void shutdown() {
        exec.shutdownNow();
    }

    public PollingScheduler(SourceRepository s, ConnectorRegistry r, IngestionService i, CircuitBreakerRegistry b) {
        this.sources = s; this.registry = r; this.ingestion = i; this.breakers = b;
    }

    @Scheduled(fixedDelay = 1000)
    public void tick() {
        long now = System.currentTimeMillis() / 1000;
        for (SourceEntity src : sources.findAllByEnabledTrueAndPollingIntervalSecIsNotNull()) {
            if (src.getPollingIntervalSec() == null || src.getPollingIntervalSec() <= 0) continue;
            if (now % src.getPollingIntervalSec() != 0) continue;
            ContainerSourceConnector connector = registry.forType(src.getConnectorType());
            SourceConfig cfg = toConfig(src);
            @SuppressWarnings("unchecked")
            List<Map<String,String>> subs = (List<Map<String,String>>) src.getConfigJson().getOrDefault("subscriptions", List.of());
            for (Map<String,String> sub : subs) {
                try {
                    UUID tenantId = UUID.fromString(sub.get("tenant_id"));
                    String containerId = sub.get("container_id");
                    pollOne(connector, cfg, tenantId, containerId);
                } catch (Exception e) {
                    log.warn("Bad subscription entry {}: {}", sub, e.toString());
                }
            }
        }
    }

    private void pollOne(ContainerSourceConnector connector, SourceConfig cfg, UUID tenantId, String containerId) {
        CircuitBreaker breaker = breakers.circuitBreaker("source-" + cfg.getName(), "source");
        long start = System.currentTimeMillis();
        try {
            if (!breaker.tryAcquirePermission()) {
                log.warn("Circuit open for source {}; skipping {}/{}", cfg.getName(), tenantId, containerId);
                return;
            }
            Future<?> future = exec.submit(() -> connector.fetch(containerId, cfg));
            @SuppressWarnings("unchecked")
            List<com.nauta.triage.domain.RawEventPayload> payloads = (List<com.nauta.triage.domain.RawEventPayload>)
                TimeLimiter.of(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(3)).build())
                    .executeFutureSupplier(() -> (Future<Object>) future);
            payloads.forEach(p -> ingestion.ingest(tenantId, cfg.getSourceId(), p));
            breaker.onSuccess(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            breaker.onError(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS, ex);
            log.warn("Poll failed for source={} container={}: {}", cfg.getName(), containerId, ex.toString());
        }
    }

    private SourceConfig toConfig(SourceEntity s) {
        return new SourceConfig(s.getId(), s.getName(), s.getConfigJson(), s.getMappingJson(), s.getWebhookSecret());
    }
}
