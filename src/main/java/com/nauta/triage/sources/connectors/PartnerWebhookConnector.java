package com.nauta.triage.sources.connectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nauta.triage.domain.RawEventPayload;
import com.nauta.triage.sources.*;
import com.nauta.triage.sources.mapping.MappingError;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
public class PartnerWebhookConnector implements ContainerSourceConnector {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override public String name() { return "partner-webhook"; }
    @Override public String connectorType() { return "partner-webhook-v1"; }
    @Override public SourceCapabilities capabilities() {
        return SourceCapabilities.builder().supportsPolling(false).supportsWebhook(true).build();
    }

    @Override public List<RawEventPayload> fetch(String c, SourceConfig cfg) {
        throw new UnsupportedOperationException("push-only");
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RawEventPayload> parseWebhook(byte[] body, SourceConfig config) {
        try {
            Map<String, Object> parsed = MAPPER.readValue(body, Map.class);
            String containerId = (String) parsed.get("container_id");
            if (containerId == null) throw new MappingError("webhook body missing container_id");
            return List.of(new RawEventPayload(containerId, parsed));
        } catch (MappingError e) {
            throw e;
        } catch (Exception e) {
            throw new MappingError("invalid webhook body", e);
        }
    }
}
