package com.nauta.triage.domain;

import lombok.Value;
import java.util.Map;

@Value
public class RawEventPayload {
    String containerBusinessId;
    Map<String, Object> raw;
}
