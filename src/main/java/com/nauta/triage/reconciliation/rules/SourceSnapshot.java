package com.nauta.triage.reconciliation.rules;

import com.nauta.triage.domain.NormalizedEvent;
import lombok.Builder;
import lombok.Value;
import java.util.List;
import java.util.UUID;

@Value @Builder
public class SourceSnapshot {
    UUID sourceId;
    String sourceName;
    boolean responded;
    List<NormalizedEvent> events;
    String responseNote;
    String declaredEta;
}
