package com.nauta.triage.sources.mapping;

public class MappingError extends RuntimeException {
    public MappingError(String msg) { super(msg); }
    public MappingError(String msg, Throwable cause) { super(msg, cause); }
}
