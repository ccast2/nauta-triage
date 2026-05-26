package com.nauta.triage.admin.error;

import java.util.Map;

public record ApiError(String error, String message, Map<String, Object> details) {}
