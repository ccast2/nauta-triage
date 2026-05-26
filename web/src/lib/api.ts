const BASE = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:18080";

export class ApiError extends Error {
  status: number;
  code?: string;
  details?: unknown;
  constructor(status: number, message: string, code?: string, details?: unknown) {
    super(message); this.status = status; this.code = code; this.details = details;
  }
}

type FetchOpts = { tenantId?: string | null; method?: string; body?: unknown };

export async function api<T = unknown>(path: string, opts: FetchOpts = {}): Promise<T> {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (opts.tenantId) headers["X-Tenant-Id"] = opts.tenantId;
  const res = await fetch(BASE + path, {
    method: opts.method ?? "GET",
    headers,
    body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
  });
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  const json = text ? JSON.parse(text) : null;
  if (!res.ok) {
    throw new ApiError(res.status, json?.message ?? res.statusText, json?.error, json?.details);
  }
  return json as T;
}
