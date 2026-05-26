package com.nauta.triage.admin;

import com.nauta.triage.admin.dto.*;
import com.nauta.triage.admin.error.AdminException;
import com.nauta.triage.persistence.entity.SourceEntity;
import com.nauta.triage.persistence.repository.SourceRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/admin/sources")
public class AdminSourceController {
    private final SourceRepository sources;

    public AdminSourceController(SourceRepository s) { this.sources = s; }

    @GetMapping
    public List<SourceSummaryDto> list() {
        return sources.findAll().stream().map(this::toSummary).toList();
    }

    @PostMapping
    public ResponseEntity<SourceDetailDto> create(@Valid @RequestBody SourceCreateRequest req) {
        if (sources.findByName(req.name()).isPresent()) {
            throw new AdminException(HttpStatus.CONFLICT, "duplicate_source_name",
                    "source with name " + req.name() + " already exists");
        }
        var saved = sources.save(SourceEntity.builder()
                .name(req.name())
                .connectorType(req.connectorType())
                .pollingIntervalSec(req.pollingIntervalSec())
                .supportsWebhook(req.supportsWebhook())
                .enabled(req.enabled())
                .configJson(req.configJson() == null ? new LinkedHashMap<>() : req.configJson())
                .mappingJson(req.mappingJson() == null ? new LinkedHashMap<>() : req.mappingJson())
                .build());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDetail(saved));
    }

    @GetMapping("/{id}")
    public SourceDetailDto get(@PathVariable UUID id) {
        return toDetail(mustFind(id));
    }

    @PatchMapping("/{id}")
    public SourceDetailDto patch(@PathVariable UUID id, @RequestBody SourcePatchRequest req) {
        var s = mustFind(id);
        if (req.enabled() != null) s.setEnabled(req.enabled());
        if (req.pollingIntervalSec() != null) s.setPollingIntervalSec(req.pollingIntervalSec());
        if (req.supportsWebhook() != null) s.setSupportsWebhook(req.supportsWebhook());
        if (req.configJson() != null) s.setConfigJson(req.configJson());
        if (req.mappingJson() != null) s.setMappingJson(req.mappingJson());
        return toDetail(sources.save(s));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        sources.delete(mustFind(id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/subscriptions")
    public Map<String, Object> addSubscription(@PathVariable UUID id, @Valid @RequestBody SubscriptionRequest req) {
        return mutateSubscriptions(id, list -> {
            var entry = Map.of("tenant_id", req.tenantId().toString(),
                    "container_id", req.containerBusinessId());
            if (!list.contains(entry)) list.add(entry);
            return list;
        });
    }

    @DeleteMapping("/{id}/subscriptions")
    public Map<String, Object> removeSubscription(@PathVariable UUID id, @RequestBody SubscriptionRequest req) {
        return mutateSubscriptions(id, list -> {
            list.removeIf(e -> req.tenantId().toString().equals(e.get("tenant_id"))
                    && req.containerBusinessId().equals(e.get("container_id")));
            return list;
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mutateSubscriptions(UUID id, java.util.function.Function<List<Map<String, String>>, List<Map<String, String>>> mutator) {
        var s = mustFind(id);
        var cfg = s.getConfigJson() == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<>(s.getConfigJson());
        var existing = (List<Map<String, String>>) cfg.getOrDefault("subscriptions", new ArrayList<>());
        var mutable = new ArrayList<>(existing);
        var updated = mutator.apply(mutable);
        cfg.put("subscriptions", updated);
        s.setConfigJson(cfg);
        sources.save(s);
        return Map.of("subscriptions", updated);
    }

    private SourceEntity mustFind(UUID id) {
        return sources.findById(id)
                .orElseThrow(() -> new AdminException(HttpStatus.NOT_FOUND, "source_not_found",
                        "source " + id + " not found"));
    }

    @SuppressWarnings("unchecked")
    private SourceSummaryDto toSummary(SourceEntity s) {
        int subs = 0;
        if (s.getConfigJson() != null) {
            var raw = s.getConfigJson().getOrDefault("subscriptions", List.of());
            if (raw instanceof List<?> l) subs = l.size();
        }
        return new SourceSummaryDto(s.getId(), s.getName(), s.getConnectorType(),
                s.isEnabled(), s.getPollingIntervalSec(), s.isSupportsWebhook(), subs);
    }

    private SourceDetailDto toDetail(SourceEntity s) {
        return new SourceDetailDto(s.getId(), s.getName(), s.getConnectorType(),
                s.isEnabled(), s.getPollingIntervalSec(), s.isSupportsWebhook(),
                s.getConfigJson(), s.getMappingJson());
    }
}
