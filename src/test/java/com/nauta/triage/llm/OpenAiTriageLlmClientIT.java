package com.nauta.triage.llm;

import com.nauta.triage.AbstractPostgresIT;
import com.nauta.triage.reconciliation.TriageLLMClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "triage.llm.enabled=true",
        "triage.llm.timeout-ms=300",
        "triage.llm.api-key=test-fake-key-not-real"
})
class OpenAiTriageLlmClientIT extends AbstractPostgresIT {
    @Autowired
    TriageLLMClient llm;

    @Test
    void returns_not_ok_when_upstream_returns_error_or_unreachable() {
        var result = llm.triage(List.of(), LocalDate.now(), "test");
        assertThat(result.isOk()).isFalse();
        // could be "timeout", "error", or "circuit_open" depending on env — all acceptable
    }
}
