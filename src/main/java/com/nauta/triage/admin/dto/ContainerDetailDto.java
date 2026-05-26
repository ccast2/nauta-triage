package com.nauta.triage.admin.dto;

import java.util.List;
import java.util.Map;

public record ContainerDetailDto(
        ContainerSummaryDto container,
        Map<String, Object> status,
        List<Map<String, Object>> decisions,
        List<Map<String, Object>> raw_events) {}
