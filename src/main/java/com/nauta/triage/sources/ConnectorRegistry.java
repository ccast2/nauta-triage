package com.nauta.triage.sources;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ConnectorRegistry {
    private final Map<String, ContainerSourceConnector> byType;

    public ConnectorRegistry(List<ContainerSourceConnector> connectors) {
        this.byType = connectors.stream()
                .collect(Collectors.toMap(ContainerSourceConnector::connectorType, c -> c));
    }

    public ContainerSourceConnector forType(String connectorType) {
        ContainerSourceConnector c = byType.get(connectorType);
        if (c == null) throw new IllegalArgumentException("Unknown connector type: " + connectorType);
        return c;
    }
}
