-- V2__core_schema.sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL,
    api_token_hash TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sources (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL UNIQUE,
    connector_type TEXT NOT NULL,
    config_json JSONB NOT NULL,
    mapping_json JSONB NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    polling_interval_sec INT,
    supports_webhook BOOLEAN NOT NULL DEFAULT FALSE,
    webhook_secret TEXT
);

CREATE TABLE containers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    container_business_id TEXT NOT NULL,
    carrier TEXT,
    pol TEXT,
    pod TEXT,
    declared_eta DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, container_business_id)
);
CREATE INDEX idx_containers_tenant ON containers(tenant_id);

CREATE TABLE raw_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    container_id_ref UUID REFERENCES containers(id),
    container_business_id TEXT NOT NULL,
    source_id UUID NOT NULL REFERENCES sources(id),
    payload_json JSONB NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ,
    processing_status TEXT NOT NULL DEFAULT 'pending',
    processing_attempts INT NOT NULL DEFAULT 0,
    last_error TEXT,
    CHECK (processing_status IN ('pending','processed','failed_validation','failed_processing'))
);
CREATE INDEX idx_raw_events_claim ON raw_events(processing_status, received_at) WHERE processing_status='pending';
CREATE INDEX idx_raw_events_container ON raw_events(tenant_id, container_business_id);

CREATE TABLE normalized_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    raw_event_id UUID NOT NULL REFERENCES raw_events(id),
    container_id UUID NOT NULL REFERENCES containers(id),
    source_id UUID NOT NULL REFERENCES sources(id),
    event_type TEXT NOT NULL,
    event_timestamp TIMESTAMPTZ NOT NULL,
    extra_json JSONB,
    normalized_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (event_type IN ('gate_out','sail','arrival','discharge'))
);
CREATE INDEX idx_norm_events_container_ts ON normalized_events(container_id, event_timestamp DESC);

CREATE TABLE llm_calls (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    called_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    model TEXT NOT NULL,
    prompt TEXT,
    response_json JSONB,
    tokens_in INT,
    tokens_out INT,
    latency_ms INT,
    status TEXT NOT NULL CHECK (status IN ('ok','timeout','error','circuit_open'))
);

CREATE TABLE decisions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    container_id UUID NOT NULL REFERENCES containers(id),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    decided_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    path TEXT NOT NULL CHECK (path IN ('rule','llm','fallback')),
    status TEXT NOT NULL CHECK (status IN ('ON_TRACK','DELAYED','NEEDS_REVIEW','LOST')),
    confidence NUMERIC(4,3) NOT NULL,
    reasoning TEXT NOT NULL,
    next_action TEXT NOT NULL CHECK (next_action IN ('wait','refresh_source','escalate_to_human','mark_lost')),
    inputs_snapshot_json JSONB NOT NULL,
    rules_fired_json JSONB,
    llm_call_id UUID REFERENCES llm_calls(id),
    latency_ms INT NOT NULL,
    superseded_by UUID REFERENCES decisions(id)
);
CREATE INDEX idx_decisions_container ON decisions(container_id, decided_at DESC);

CREATE TABLE container_states (
    container_id UUID PRIMARY KEY REFERENCES containers(id),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    status TEXT NOT NULL CHECK (status IN ('ON_TRACK','DELAYED','NEEDS_REVIEW','LOST')),
    confidence NUMERIC(4,3) NOT NULL,
    reasoning TEXT NOT NULL,
    next_action TEXT NOT NULL CHECK (next_action IN ('wait','refresh_source','escalate_to_human','mark_lost')),
    reconciled_eta DATE,
    reconciled_etd DATE,
    decided_by_path TEXT NOT NULL CHECK (decided_by_path IN ('rule','llm','fallback')),
    last_decision_id UUID NOT NULL REFERENCES decisions(id),
    last_source_refresh_at TIMESTAMPTZ NOT NULL,
    version INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_states_tenant ON container_states(tenant_id);
