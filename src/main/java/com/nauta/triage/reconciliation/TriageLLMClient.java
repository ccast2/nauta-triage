package com.nauta.triage.reconciliation;

import com.nauta.triage.reconciliation.rules.SourceSnapshot;
import lombok.Value;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TriageLLMClient {
    LlmTriageResult triage(List<SourceSnapshot> snapshots, LocalDate declaredEta, String inconclusiveReason);

    @Value
    class LlmTriageResult {
        boolean ok;
        com.nauta.triage.domain.ContainerStatus status;
        double selfReportedConfidence;
        String reasoning;
        UUID llmCallId;
    }
}
