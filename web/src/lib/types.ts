export type ContainerStatus = "ON_TRACK" | "DELAYED" | "NEEDS_REVIEW" | "LOST";
export type DecisionPath = "rule" | "llm" | "fallback";

export interface Tenant { id: string; name: string; demo_token: string | null; }

export interface ContainerSummary {
  id: string;
  business_id: string;
  carrier: string | null;
  pol: string | null;
  pod: string | null;
  declared_eta: string | null;
  current_status: ContainerStatus | null;
  current_confidence: number | null;
  next_action: string | null;
  updated_at: string;
}

export interface ContainerDetail {
  container: ContainerSummary;
  status: {
    status: ContainerStatus; confidence: number; reasoning: string;
    next_action: string; decided_by_path: DecisionPath; updated_at: string;
  } | null;
  decisions: Array<{
    id: string; decided_at: string; path: DecisionPath;
    status: ContainerStatus; confidence: number; reasoning: string;
    next_action: string; latency_ms: number; inputs_snapshot: unknown;
  }>;
  raw_events: Array<{
    id: string; source_id: string; received_at: string;
    processed_at: string | null; processing_status: string | null;
    payload: unknown;
  }>;
}

export interface SourceSummary {
  id: string; name: string; connector_type: string; enabled: boolean;
  polling_interval_sec: number | null; supports_webhook: boolean;
  subscriptions_count: number;
}

export interface SourceDetail extends Omit<SourceSummary, "subscriptions_count"> {
  config_json: Record<string, unknown>;
  mapping_json: Record<string, unknown>;
}

export interface Simulator { source_name: string; base_url: string; reachable: boolean; }

export interface Stub { id: string; container_id: string; status: number; body: Record<string, unknown>; }
