package com.nauta.triage.sources;

import com.nauta.triage.domain.RawEventPayload;
import java.util.List;

public interface ContainerSourceConnector {
    String name();
    String connectorType();
    SourceCapabilities capabilities();
    List<RawEventPayload> fetch(String containerBusinessId, SourceConfig config);
    default List<RawEventPayload> parseWebhook(byte[] body, SourceConfig config) {
        throw new UnsupportedOperationException(name() + " does not support webhooks");
    }
}
