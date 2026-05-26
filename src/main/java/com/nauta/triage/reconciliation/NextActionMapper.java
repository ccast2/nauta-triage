package com.nauta.triage.reconciliation;

import com.nauta.triage.domain.ContainerStatus;
import com.nauta.triage.domain.NextAction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NextActionMapper {
    private final double threshold;
    public NextActionMapper(@Value("${triage.auto-resolve-threshold:0.80}") double t) { this.threshold = t; }

    public NextAction map(ContainerStatus status, double confidence, double coverage) {
        if (status == ContainerStatus.LOST) return NextAction.mark_lost;
        if (status == ContainerStatus.NEEDS_REVIEW) return NextAction.escalate_to_human;
        if (confidence >= threshold) return NextAction.wait;
        return coverage < 1.0 ? NextAction.refresh_source : NextAction.escalate_to_human;
    }
}
