package com.nauta.triage.admin.dto;

import java.util.Map;

public record StubUpsertRequest(Integer status, Map<String, Object> body) {}
