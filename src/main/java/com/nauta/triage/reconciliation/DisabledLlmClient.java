package com.nauta.triage.reconciliation;

import com.nauta.triage.reconciliation.rules.SourceSnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.List;

@Component
@Primary
@ConditionalOnProperty(name = "triage.llm.enabled", havingValue = "false")
public class DisabledLlmClient implements TriageLLMClient {
    @Override
    public LlmTriageResult triage(List<SourceSnapshot> s, LocalDate eta, String reason) {
        return new LlmTriageResult(false, null, 0.0, "LLM disabled", null);
    }
}
