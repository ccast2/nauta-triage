package com.nauta.triage.reconciliation.rules;

import com.nauta.triage.domain.ContainerStatus;
import lombok.Value;
import java.util.List;

@Value
public class RuleDecision {
    boolean decided;
    ContainerStatus status;
    double baseConfidence;
    String reasoning;
    List<String> rulesFired;
    String inconclusiveReason;

    public static RuleDecision decided(ContainerStatus s, double conf, String reasoning, List<String> rulesFired) {
        return new RuleDecision(true, s, conf, reasoning, rulesFired, null);
    }
    public static RuleDecision inconclusive(String reason, List<String> rulesFired) {
        return new RuleDecision(false, null, 0.0, null, rulesFired, reason);
    }
}
