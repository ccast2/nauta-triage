package com.nauta.triage.sources.connectors;

import com.nauta.triage.domain.RawEventPayload;
import com.nauta.triage.sources.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Map;

@Component
public class TerminalFeedConnector implements ContainerSourceConnector {
    @Override public String name() { return "terminal-feed"; }
    @Override public String connectorType() { return "terminal-feed-v1"; }
    @Override public SourceCapabilities capabilities() {
        return SourceCapabilities.builder().supportsPolling(true).supportsWebhook(false).build();
    }

    @Override
    public List<RawEventPayload> fetch(String containerBusinessId, SourceConfig config) {
        String baseUrl = (String) config.getConfigJson().get("base_url");
        var client = RestClient.builder().baseUrl(baseUrl).build();
        @SuppressWarnings("unchecked")
        Map<String, Object> body = client.get()
            .uri("/terminal/{id}", containerBusinessId)
            .retrieve()
            .body(Map.class);
        return body == null ? List.of() : List.of(new RawEventPayload(containerBusinessId, body));
    }
}
