package com.nauta.triage.llm;

import com.nauta.triage.reconciliation.rules.SourceSnapshot;

import java.time.LocalDate;
import java.util.List;

public final class TriagePromptBuilder {
    private TriagePromptBuilder() {
    }

    public static String build(List<SourceSnapshot> snapshots, LocalDate declaredEta, String inconclusiveReason) {
        StringBuilder b = new StringBuilder();
        b.append("You are a logistics triage assistant. The deterministic rule engine could not decide because: ")
                .append(inconclusiveReason).append(".\n\n");
        b.append("Declared ETA: ").append(declaredEta).append("\n\n");
        b.append("Sources:\n");
        for (var s : snapshots) {
            b.append("- ").append(s.getSourceName()).append(" (responded=").append(s.isResponded()).append(")\n");
            for (var e : s.getEvents()) {
                b.append("    ").append(e.getType()).append(" @ ").append(e.getTimestamp()).append("\n");
            }
            if (s.getResponseNote() != null) {
                b.append("    note: ").append(s.getResponseNote()).append("\n");
            }
        }
        b.append("\nDecide: ON_TRACK, DELAYED, NEEDS_REVIEW, or LOST. ")
                .append("Be conservative — if data is genuinely ambiguous, return NEEDS_REVIEW. ")
                .append("Respond with a JSON object: {\"status\":\"...\", \"confidence\":0.0-1.0, \"reasoning\":\"...\"}.");
        return b.toString();
    }
}
