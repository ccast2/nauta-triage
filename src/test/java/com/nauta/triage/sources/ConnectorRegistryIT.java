package com.nauta.triage.sources;

import com.nauta.triage.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;

class ConnectorRegistryIT extends AbstractPostgresIT {
    @Autowired ConnectorRegistry registry;

    @Test
    void resolves_all_three_connectors() {
        assertThat(registry.forType("carrier-portal-v1").name()).isEqualTo("carrier-portal");
        assertThat(registry.forType("terminal-feed-v1").name()).isEqualTo("terminal-feed");
        assertThat(registry.forType("partner-webhook-v1").name()).isEqualTo("partner-webhook");
    }
}
