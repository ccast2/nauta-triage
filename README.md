# Container Status Triage Service

A multi-tenant service that ingests container tracking events from heterogeneous
external sources, reconciles them with a deterministic rule router, falls back
to an LLM only when the rules are inconclusive, and exposes a tenant-scoped
HTTP API for the current status, reasoning, and full decision history of each
container. Ships with a shadcn/React control panel to drive the system end to
end without touching the database.

---

## Table of contents

1. [One-command run](#one-command-run)
2. [Demo bearer tokens](#demo-bearer-tokens)
3. [Demo curl commands](#demo-curl-commands)
4. [Frontend control panel](#frontend-control-panel)
5. [What the system does (functional overview)](#what-the-system-does-functional-overview)
6. [Data model](#data-model)
7. [End-to-end flow with example](#end-to-end-flow-with-example)
8. [The seven rules (R1–R7)](#the-seven-rules-r1r7)
9. [Mappings: translating provider JSON to canonical events](#mappings-translating-provider-json-to-canonical-events)
10. [HMAC signatures (webhooks only)](#hmac-signatures-webhooks-only)
11. [Technical reference: per-flow walkthrough](#technical-reference-per-flow-walkthrough)
12. [Threading model](#threading-model)
13. [Failure modes and how they are handled](#failure-modes-and-how-they-are-handled)
14. [Runtime configuration](#runtime-configuration)
15. [SPA pages reference](#spa-pages-reference)
16. [Tracing an event end to end](#tracing-an-event-end-to-end)
17. [Tests](#tests)
18. [External sources and demo dataset](#external-sources-and-demo-dataset)
19. [Design decisions](#design-decisions)
20. [Known limitations (MVP)](#known-limitations-mvp)

---

## One-command run

```bash
docker compose up --build
```

Services that come up:

| Service          | Port  | What it is                                          |
| ---------------- | ----- | --------------------------------------------------- |
| `app`            | 18080 | The Spring Boot triage service                      |
| `db`             | 15432 | Postgres 16 (schema applied by Flyway on startup)   |
| `carrier-portal` | 19001 | WireMock simulator for the carrier portal connector |
| `terminal-feed`  | 19002 | WireMock simulator for the terminal feed connector  |
| `web`            | 13000 | shadcn/React control panel                          |

Host ports are overridable via `APP_HOST_PORT`, `DB_HOST_PORT`,
`CARRIER_PORTAL_HOST_PORT`, `TERMINAL_FEED_HOST_PORT`, `WEB_HOST_PORT`
(see `.env.example`).

The seed migration registers two tenants, two demo sources pointing at the
WireMock services, and seven demo containers covering each rule path. The
polling scheduler picks them up automatically on boot.

To exercise the LLM path, provide an `OPENAI_API_KEY`:

```bash
cp .env.example .env
# edit .env and set OPENAI_API_KEY=sk-...
docker compose up --build
```

Without a key the service still runs and degrades to the rule + fallback
paths only (LLM-inconclusive cases land on `NEEDS_REVIEW`). The model
defaults to `gpt-4o-mini` and can be overridden via `OPENAI_MODEL`.

---

## Demo bearer tokens

Two tenants are seeded. Authenticate with `Authorization: Bearer <token>`:

| Tenant   | Token            | SHA-256 of token (stored in DB)                                    |
| -------- | ---------------- | ------------------------------------------------------------------ |
| tenant-a | `demo-token-a`   | `5307bdd134e2317a34aa9bbf9ec1c1971a3314ac2069e8d869ad80088abcbe82` |
| tenant-b | `demo-token-b`   | `083bc230ce904b8d38735f47857e93be5ad886b1863ba4fbd223038c94a6c0d8` |

Tokens are never stored in plaintext; the auth interceptor hashes the incoming
bearer with SHA-256 and looks up `tenants.api_token_hash`.

---

## Demo curl commands

Wait ~15 seconds after `docker compose up` for the polling scheduler to fetch
the simulators at least once.

```bash
# Happy path: both sources agree, ETA in the future -> ON_TRACK (rule R2)
curl -s -H "Authorization: Bearer demo-token-a" \
     http://localhost:18080/containers/MSCU-HAPPY/status | jq

# Conflict: sources disagree by > 6h -> NEEDS_REVIEW (rule R5 inconclusive -> fallback)
curl -s -H "Authorization: Bearer demo-token-a" \
     http://localhost:18080/containers/MSCU-CONFLICT/status | jq

# Decision history (audit log)
curl -s -H "Authorization: Bearer demo-token-a" \
     http://localhost:18080/containers/MSCU-HAPPY/decisions | jq

# Tenant isolation: tenant-b sees its own MSCU-HAPPY, not tenant-a's
curl -s -H "Authorization: Bearer demo-token-b" \
     http://localhost:18080/containers/MSCU-HAPPY/status | jq
```

---

## Frontend control panel

Open <http://localhost:13000>. The SPA talks to the backend at
`localhost:18080`. On first load:

1. **Tenants** → click **Use** next to `tenant-a` (sets the current tenant).
2. **Sources** → see seeded sources; click into one to edit `config_json`,
   `mapping_json`, or manage subscriptions.
3. **Stubs** → click `carrier-portal` or `terminal-feed` to add/edit the
   JSON each WireMock simulator returns. Changes apply immediately; the
   next polling tick will ingest them.

### Manual smoke flow

1. **Tenants** → select `tenant-a`.
2. **Dashboard** → see seeded containers and current status.
3. Click `MSCU-CONFLICT` → see Decisions timeline (rule / LLM / fallback)
   and Raw events tab.
4. **Stubs** → `carrier-portal` → edit `MSCU-HAPPY`'s `eta` to a far past
   date. Save. Within the source's polling interval a new decision lands
   in `/containers/MSCU-HAPPY`, likely flipping to `DELAYED` /
   `NEEDS_REVIEW`.
5. **Containers** → **+ New container** → e.g. `MSCU-PLAY`, ETA 2026-12-01.
6. **Sources** → `terminal-feed-demo` → Subscriptions tab → enter
   `MSCU-PLAY` → Subscribe.
7. **Stubs** → `terminal-feed` → + Add stub → `MSCU-PLAY`, body
   `{"events":[{"type":"gate_out","date":"2026-11-15T10:00:00Z"}],"eta":"2026-12-01"}`.
8. Watch `/containers/MSCU-PLAY` reconcile in real time.

---

## What the system does (functional overview)

You have shipping containers (metal boxes on ships). Different external
sources report things about them: the carrier, the destination terminal,
partners. These sources:

- Do not always agree.
- Sometimes lag or go down.
- Speak different formats (each one has its own JSON shape).

The triage service:

1. **Ingests** events from all sources (polling or webhook).
2. **Normalizes** each payload into a canonical internal format.
3. **Reconciles** events from multiple sources into a single status:
   `ON_TRACK`, `DELAYED`, `NEEDS_REVIEW`, `LOST`.
4. **Decides** using a deterministic rule router (R1..R7) and, if the rules
   cannot reach a confident answer, consults an LLM.
5. **Exposes** the current status and decision history over HTTP, scoped per
   tenant via bearer token.
6. **Audits** every raw event, every decision, every LLM call.

### Stack

| Piece            | Technology                       | Purpose                                                     |
| ---------------- | -------------------------------- | ----------------------------------------------------------- |
| `app`            | Spring Boot 3.3 / Java 21        | HTTP API, polling scheduler, reconciliation worker          |
| `db`             | Postgres 16                      | Persistence (Flyway migrates the schema on boot)            |
| `carrier-portal` | WireMock                         | Simulates the carrier's REST API                            |
| `terminal-feed`  | WireMock                         | Simulates the terminal's REST API                           |
| `web`            | Vite + React + shadcn/ui         | Control panel SPA                                           |
| LLM              | OpenAI Chat Completions          | Triage when rules cannot reach a confident answer           |

---

## Data model

### `tenants`
Who consumes the API. Each tenant has an `api_token_hash` (SHA-256 of the
bearer token). The demo seeds are `tenant-a` and `tenant-b`.

### `sources`
Definition of an external source. Key columns:

- `name` — unique identifier (e.g. `carrier-portal-demo`).
- `connector_type` — which Java class knows how to talk to it
  (`carrier-portal-v1`, `terminal-feed-v1`, `partner-webhook-v1`).
- `config_json` — connector-specific configuration. **Critical**: contains a
  `subscriptions` array of `{tenant_id, container_id}` that tells the
  scheduler "you must poll this container for this tenant".
- `mapping_json` — how to translate the provider's own JSON to the canonical
  event shape (see [Mappings](#mappings-translating-provider-json-to-canonical-events)).
- `enabled`, `polling_interval_sec`, `supports_webhook`, `webhook_secret` —
  operational flags.

### `containers`
The physical box, identified by `(tenant_id, container_business_id)`, e.g.
`(tenant-a, MSCU-HAPPY)`. Holds metadata: `carrier`, `pol` (port of loading),
`pod` (port of discharge), `declared_eta`.

### `raw_events`
Every response received from a source is stored **as-is** here, uninterpreted.
Acts as a work queue: has `processing_status ∈ pending/processed/failed`. The
source of truth for audit: "what exactly did the carrier say at 14:32".

### `normalized_events`
The raw event mapped into the canonical shape: `{container_id, source_id,
event_type, event_timestamp, extra}`. `event_type` is a closed enum: `sail`,
`discharge`, `arrival`, `gate_in`, `gate_out`, `eta_update`.

### `decisions`
Every time the worker reconciles, an immutable row is appended here. Includes
`path ∈ rule|llm|fallback`, `status`, `confidence`, `reasoning`,
`inputs_snapshot_json` (which events were seen to decide), `latency_ms`. **A
decision is never modified**: if new information arrives, a new row is written
and the previous one is linked via `superseded_by`.

### `container_states`
The "current" view — one row per container with the latest status and a
`last_decision_id` pointing back to `decisions`. This is what
`GET /containers/{id}/status` reads.

### `llm_calls`
Audit of every OpenAI call: prompt, response, latency, status
(`ok/timeout/error/circuit_open`). Useful for debugging.

---

## End-to-end flow with example

A concrete walkthrough using a payload the demo carrier returns for `MSCU-HAPPY`:

```json
{
  "container_id": "MSCU-HAPPY",
  "events": [
    { "type": "gate_out", "date": "2026-05-01T10:00:00Z" },
    { "type": "sail",     "date": "2026-05-05T03:00:00Z" }
  ],
  "eta": "2026-06-15"
}
```

### Step 1 — Ingestion (polling)

The `PollingScheduler` runs every `polling_interval_sec` (10s in the seed).
For each enabled source with an interval, it reads
`config_json.subscriptions` and for each `{tenant_id, container_id}` it
calls the appropriate connector:

```
CarrierPortalConnector.fetch("MSCU-HAPPY", source)
  → GET http://carrier-portal:8080/containers/MSCU-HAPPY
```

WireMock returns the JSON above. The connector returns a `RawEventPayload`
that `IngestionService` writes into `raw_events` with
`processing_status='pending'`.

### Step 2 — Normalization

The `ReconciliationWorker` runs in a loop, claiming batches of pending
`raw_events` with `SELECT ... FOR UPDATE SKIP LOCKED` (multiple replicas
can process in parallel without stepping on each other).

For each raw event, `MappingEngine` applies the source's `mapping_json`
and produces rows in `normalized_events`:

```
NormalizedEvent { source=carrier-portal-demo, container=MSCU-HAPPY,
                  type=gate_out, timestamp=2026-05-01T10:00:00Z }
NormalizedEvent { ... type=sail, timestamp=2026-05-05T03:00:00Z }
```

### Step 3 — Reconciliation

The worker collects **all** normalized events from **all** sources for that
container (not just the ones from the raw event that just arrived), builds
one `SourceSnapshot` per responding source, and passes them to the
`RuleRouter`.

### Step 4 — Rules R1..R7

The router evaluates rules in strict order; the first match decides.

In this example: both sources respond, both report `gate_out + sail`, ETA
still in the future → **R2** fires → `ON_TRACK` with confidence 0.92. A row
is written to `decisions` with `path='rule'`.

### Step 5 — LLM fallback (when rules are inconclusive)

If the rule that fired was inconclusive (R7/R5/R1/R6),
`ReconciliationService` calls `OpenAiTriageLlmClient`:

- Builds the prompt via `TriagePromptBuilder`.
- Calls `POST https://api.openai.com/v1/chat/completions` with
  `response_format: json_object` and a fixed system prompt:
  > "Return ONLY a JSON object with keys: status, confidence, reasoning"
- If OpenAI responds in time (2s timeout) and the response parses, the
  decision is written with `path='llm'`.
- If the LLM fails (timeout, error, breaker open), the deterministic
  fallback runs (`path='fallback'`) emitting `NEEDS_REVIEW` with
  `next_action='escalate_to_human'`.

Every attempt is recorded in `llm_calls`.

### Step 6 — Persisting the state

The worker UPSERTs `container_states` with the latest result. That row is
what `GET /containers/{id}/status` reads directly, no re-reconciliation
needed.

### Step 7 — Reading from the API

The SPA calls `GET /admin/containers/MSCU-HAPPY` and receives:

```json
{
  "container":  { "business_id": "MSCU-HAPPY", "current_status": "ON_TRACK", ... },
  "status":     { "status": "ON_TRACK", "confidence": 0.92, "reasoning": "Sources agree...", "decided_by_path": "rule" },
  "decisions":  [ ... chronological timeline ... ],
  "raw_events": [ ... what each simulator returned ... ]
}
```

---

## The seven rules (R1–R7)

The router evaluates them in this order. The first match decides
(decisively or inconclusively):

| Order | Rule | Condition                                                                                     | Result                              |
| ----- | ---- | --------------------------------------------------------------------------------------------- | ----------------------------------- |
| 1     | R7   | Within the **same source**, events out of logical order (`arrival` before `sail`)             | Inconclusive → LLM                  |
| 2     | R5   | Two sources report the **same event type** with timestamps differing > 6 h                    | Inconclusive → LLM                  |
| 3     | R4   | Any source reports `arrival`                                                                  | `ON_TRACK` (0.90 base confidence)   |
| 4     | R3   | Past `declared_eta + 24h` and no `arrival` event                                              | `DELAYED` (0.88)                    |
| 5     | R1   | Last event is older than 14 days and no `arrival` / `discharge`                               | Inconclusive → LLM (LOST candidate) |
| 6     | R6   | Only 1 source responded when ≥ 2 were expected                                                | Inconclusive → LLM (sparse coverage)|
| 7     | R2   | ≥ 2 sources respond, agree on the event sequence, no `discharge` yet                          | `ON_TRACK` (0.92)                   |

Three decision paths emerge:

| `path`     | When                                                              | Meaning                                                   |
| ---------- | ----------------------------------------------------------------- | --------------------------------------------------------- |
| `rule`     | R2/R3/R4 fired decisively                                         | Most trustworthy, no LLM involvement                      |
| `llm`      | Rule was inconclusive and the LLM responded successfully          | Confidence varies based on model's self-report            |
| `fallback` | LLM failed (timeout / breaker open / error / disabled)            | Always `NEEDS_REVIEW` with `next_action='escalate_to_human'` |

This separation is what makes the LLM safe to depend on: it is **never** on
the critical path. If OpenAI is down, the system keeps working and honestly
emits `NEEDS_REVIEW` instead of guessing.

---

## Mappings: translating provider JSON to canonical events

Every source returns its own JSON. The connector stores it raw, then
`MappingEngine` translates it using the `mapping_json` defined for that
source.

### The six fields

```json
{
  "container_id_path":     "$.container_id",
  "events_path":           "$.events",
  "event_type_path":       "$.type",
  "event_timestamp_path":  "$.date",
  "event_type_map":        { "ARRIVED": "arrival", "DEPARTED": "sail" },
  "eta_path":              "$.eta",
  "note_path":             "$.note"
}
```

- `container_id_path` (**required**) — JsonPath to the container ID inside the payload.
- `events_path` (**required**) — JsonPath that resolves to an **array** of events.
- `event_type_path` (**required**) — JsonPath relative to each array element, pointing at the event type.
- `event_timestamp_path` (**required**) — JsonPath relative to each element, pointing at the timestamp.
- `event_type_map` (optional) — dictionary translating provider names to the canonical enum (`arrival`, `sail`, `gate_in`, etc.). If the provider already uses canonical names, omit it.
- `eta_path` (optional) — JsonPath to the updated ETA, stored in `extra.declared_eta` of the normalized event.
- `note_path` (optional) — free-text note, stored in `extra.note`.

### Example: provider with non-canonical field names

Suppose a provider returns:

```json
{
  "containerNo": "MSCU-X",
  "milestones": [
    { "code": "ARRIVED", "ts": "2026-06-14T18:00:00Z" },
    { "code": "DEPARTED", "ts": "2026-05-05T03:00:00Z" }
  ]
}
```

The matching `mapping_json` is:

```json
{
  "container_id_path":    "$.containerNo",
  "events_path":          "$.milestones",
  "event_type_path":      "$.code",
  "event_timestamp_path": "$.ts",
  "event_type_map":       { "ARRIVED": "arrival", "DEPARTED": "sail" }
}
```

A new source with a different shape can be added with configuration alone,
no Java code change.

### Implementation notes

- `JsonPath` (Jayway) handles expressions like `$.container_id`. The engine
  is configured with `DEFAULT_PATH_LEAF_TO_NULL` and `SUPPRESS_EXCEPTIONS`,
  so missing paths return null instead of throwing.
- `EventType.valueOf(...)` is strict: if after applying `event_type_map` the
  type still does not match the enum, that single event is dropped silently;
  the rest of the payload is still processed.
- `parseTimestamp` first tries `Instant.parse(...)` (ISO with `Z`). If that
  fails, it tries `LocalDate.parse(...).atStartOfDay(UTC)`. If both fail,
  `MappingError` is thrown and the raw event enters the retry path.

---

## HMAC signatures (webhooks only)

HMAC signatures are used only when a source **pushes** events to us via
webhook. The polling mode (current demo setup) does not use signatures
because the service is the one initiating the call.

### How the webhook validation works

Each source with `supports_webhook=true` has a `webhook_secret` stored in
the DB. When the partner sends an event:

```
POST /webhooks/{tenantId}/sources/{sourceName}
Content-Type: application/json
X-Signature: 7b3f...   ← HMAC-SHA256(body, secret) in hex

{ "container_id": "MSCU-X", "events": [...] }
```

`HmacValidator` receives:

1. The **raw body bytes** (exact bytes of the request).
2. The `X-Signature` header.
3. The `webhook_secret` from `sources.webhook_secret`.

It computes `HMAC-SHA256(body, secret)` and compares against `X-Signature`
using constant-time comparison (`MessageDigest.isEqual`) to defeat timing
attacks. The shared secret never travels over the wire — only the HMAC
of the body does.

### Generating the signature on the partner side

In any language:

```bash
echo -n '{"container_id":"MSCU-X","events":[]}' | \
  openssl dgst -sha256 -hmac "the-shared-secret" -hex
```

Result: `7b3f9c0a...` — goes into the `X-Signature` header.

---

## Technical reference: per-flow walkthrough

### Bootstrap: what starts when

`TriageApplication` is `@SpringBootApplication + @EnableScheduling`. On boot:

1. **Flyway** runs before Hibernate. Applies `V1__baseline.sql`,
   `V2__core_schema.sql`, `V3__seed_demo.sql`,
   `V4__cascade_container_delete.sql`.
2. **Spring** component-scans and instantiates singletons:
   - `ConnectorRegistry` — receives an injected `List<ContainerSourceConnector>`
     (3 implementations) and indexes them by `connectorType()` string.
   - `PollingScheduler` — registers `@Scheduled(fixedDelay = 1000)`.
   - `ReconciliationWorker` — registers
     `@Scheduled(fixedDelayString = "${triage.reconciliation.worker-poll-interval-ms:500}")`.
   - `OpenAiTriageLlmClient` (if `triage.llm.enabled=true`) or
     `DisabledLlmClient`.
3. **Tomcat** binds `:8080` inside the container (host `:18080`) and exposes
   the REST controllers.

Three independent loops run concurrently from this point:

- The polling loop (timer thread `scheduling-1`).
- The reconciliation loop (same `scheduling-1`; `@Scheduled` tasks are
  serialized in the default pool).
- The HTTP server (`http-nio-*` threads).

### Flow 1 — Polling: scheduler → raw_events

**Entry:** `PollingScheduler.tick()`.

```java
@Scheduled(fixedDelay = 1000)
public void tick() {
    long now = System.currentTimeMillis() / 1000;
    for (SourceEntity src : sources.findAllByEnabledTrueAndPollingIntervalSecIsNotNull()) {
        if (now % src.getPollingIntervalSec() != 0) continue;   // gate by interval
        ...
    }
}
```

- **Trigger:** every 1 s.
- **Source selection:**
  ```sql
  SELECT * FROM sources WHERE enabled = true AND polling_interval_sec IS NOT NULL
  ```
- **Interval gate:** `now % polling_interval_sec != 0` means polling only
  fires when `epoch_seconds` is divisible by the interval. With
  `polling_interval_sec = 10`, polling only fires when the clock hits
  `...00, 10, 20, 30, 40, 50` s. All sources with the same interval poll at
  the same time (it's a global grid, not a round-robin).

For each source that passes the gate:

```java
ContainerSourceConnector connector = registry.forType(src.getConnectorType());
List<Map<String,String>> subs = src.getConfigJson().getOrDefault("subscriptions", List.of());
for (Map<String,String> sub : subs) {
    pollOne(connector, cfg, UUID.fromString(sub.get("tenant_id")), sub.get("container_id"));
}
```

`pollOne` adds resilience:

```java
CircuitBreaker breaker = breakers.circuitBreaker("source-" + cfg.getName(), "source");
if (!breaker.tryAcquirePermission()) return;             // breaker open: skip

Future<?> future = exec.submit(() -> connector.fetch(containerId, cfg));
List<RawEventPayload> payloads = TimeLimiter
    .of(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(3)).build())
    .executeFutureSupplier(() -> (Future<Object>) future);

payloads.forEach(p -> ingestion.ingest(tenantId, cfg.getSourceId(), p));
breaker.onSuccess(...);
```

- `exec` is a `Executors.newCachedThreadPool` of daemons. Each `fetch` runs
  in its own thread so the scheduler tick is never blocked.
- `TimeLimiter` cancels the future after 3 s and throws `TimeoutException`,
  which the catch turns into `breaker.onError(...)` + a warning.
- The breaker `source-{name}` uses the `source` shared config in
  `application.yml` (50% failure threshold over a 20-call window, opens for
  30 s).

The three connectors only differ in the URL template they hit:

| Connector                  | `connectorType()`     | URL template                    |
| -------------------------- | --------------------- | ------------------------------- |
| `CarrierPortalConnector`   | `carrier-portal-v1`   | `GET {base_url}/containers/{id}`|
| `TerminalFeedConnector`    | `terminal-feed-v1`    | `GET {base_url}/terminal/{id}`  |
| `PartnerWebhookConnector`  | `partner-webhook-v1`  | n/a (webhook only)              |

`IngestionService.ingest()` writes one row to `raw_events` with
`processing_status='pending'`. The JSON is stored as-is; no interpretation
happens at this layer.

```
PollingScheduler.tick() [1 s]
  └─ for src in sources (enabled, polling) where epoch % interval == 0:
       └─ for sub in src.config_json.subscriptions:
            └─ pollOne(connector, cfg, tenantId, containerId)
                 ├─ circuit breaker check
                 ├─ exec.submit(() -> connector.fetch(...))
                 │      └─ HTTP GET /containers/{id} or /terminal/{id}
                 ├─ TimeLimiter 3 s
                 └─ ingestion.ingest(...)
                      └─ INSERT INTO raw_events (..., status='pending')
```

### Flow 2 — Webhook: HTTP push → raw_events

**Entry:** `WebhookController.webhook(...)`.

```java
@PostMapping("/webhooks/{tenantId}/sources/{sourceName}")
public ResponseEntity<Void> webhook(@PathVariable UUID tenantId,
                                    @PathVariable String sourceName,
                                    @RequestHeader(value = "X-Signature", required = false) String sig,
                                    @RequestBody byte[] body)
```

`@RequestBody byte[]` is intentional: we need the exact bytes to validate
HMAC. A `String` or `Map` parameter would let Jackson re-serialize and
break the signature.

```java
SourceEntity source = sources.findByName(sourceName).orElse(null);
if (source == null || !source.isEnabled() || !source.isSupportsWebhook()) return 404;
if (!HmacValidator.valid(body, sig, source.getWebhookSecret())) return 401;

ContainerSourceConnector connector = registry.forType(source.getConnectorType());
var payloads = connector.parseWebhook(body, cfg);
payloads.forEach(p -> ingestion.ingest(tenantId, source.getId(), p));
return ResponseEntity.accepted();   // 202
```

The response is `202 Accepted`, not `200 OK`: reconciliation is asynchronous,
so we acknowledge receipt without making the partner wait for the worker.

```
POST /webhooks/{tenant}/sources/{name}
  ├─ source lookup + enabled + supports_webhook → 404
  ├─ HmacValidator.valid(body, sig, secret)     → 401
  ├─ connector.parseWebhook(body, cfg)
  ├─ ingestion.ingest(...) (1+ raw_events)
  └─ 202 Accepted
```

### Flow 3 — Reconciliation: raw_events → decisions

**Entry:** `ReconciliationWorker.tick()`.

```java
@Scheduled(fixedDelayString = "${triage.reconciliation.worker-poll-interval-ms:500}")
public void tick() {
    var batch = txTemplate.execute(s -> raw.claimBatchSystemWide(batchSize));
    if (batch == null || batch.isEmpty()) return;
    for (var ev : batch) {
        try { txTemplate.executeWithoutResult(s -> processOne(ev)); }
        catch (Exception e) { /* already handled in processOne */ }
    }
}
```

**Trigger:** every 500 ms by default.

**Distributed claim** (`claimBatchSystemWide`):

```sql
SELECT * FROM raw_events
WHERE processing_status = 'pending'
ORDER BY received_at ASC
FOR UPDATE SKIP LOCKED
LIMIT :limit
```

`FOR UPDATE SKIP LOCKED` is what makes horizontal scaling work:

- `FOR UPDATE` takes a per-row lock.
- `SKIP LOCKED` means: if the row is already locked by another transaction,
  skip it silently instead of blocking.

If N replicas run this query at the same time, each one claims a disjoint
batch. No external coordination (ZooKeeper, Redis locks) is required. The
lock releases when the claim transaction commits, but the rows immediately
get their `processing_status` updated to `processed` or `failed_processing`
in `processOne`, so the next worker's `WHERE processing_status='pending'`
filter naturally excludes them.

**`processOne(ev)`** runs three steps sequentially within a transaction:

```java
private void processOne(RawEventEntity ev) {
    try {
        var normalized = normalizer.normalize(ev);                              // (1)
        if (!normalized.isEmpty()) {
            reconciliation.reconcile(normalized.get(0).getContainerId());       // (2)
        }
        ev.setProcessedAt(Instant.now());
        ev.setProcessingStatus("processed");                                    // (3)
        raw.save(ev);
    } catch (Exception e) {
        int newAttempts = ev.getProcessingAttempts() + 1;
        ev.setProcessingAttempts(newAttempts);
        ev.setProcessingStatus(newAttempts >= 3 ? "failed_processing" : "pending");
        ev.setLastError(e.toString());
        raw.save(ev);
    }
}
```

Failures stay `pending` for up to 3 attempts, then settle on
`failed_processing` and are no longer retried.

#### Step (1): `EventNormalizer.normalize(rawEvent)`

```java
SourceEntity source = sources.findById(raw.getSourceId()).orElseThrow();
ContainerEntity container = resolver.resolveOrCreate(raw.getTenantId(), raw.getContainerBusinessId());

List<NormalizedEvent> mapped = mapping.normalize(
    raw.getPayloadJson(), source.getMappingJson(),
    source.getId(), source.getName());
```

- `ContainerResolver.resolveOrCreate(...)` looks up the container by
  `(tenant_id, business_id)`. If it does not exist it is created with only
  the business id — this is why a webhook for an unknown container does
  not crash the pipeline; it auto-registers.
- `MappingEngine.normalize(...)` applies the source's `mapping_json` to the
  payload (see [Mappings](#mappings-translating-provider-json-to-canonical-events)).
- Each resulting `NormalizedEvent` is persisted with a foreign key to its
  origin raw event (audit trail).

#### Step (2): `ReconciliationService.reconcile(containerId)`

```java
@Transactional
public DecisionEntity reconcile(UUID containerId) {
    ContainerEntity container = containers.findById(containerId).orElseThrow();
    List<SourceEntity> enabledSources = sources.findAllByEnabledTrue();

    List<SourceSnapshot> snapshots = buildSnapshots(containerId, enabledSources);
    RuleDecision rd = router.evaluate(snapshots, container.getDeclaredEta(), Instant.now());

    if (rd.isDecided()) { /* path=rule */ }
    else {
        var llmResult = llm.triage(snapshots, container.getDeclaredEta(), rd.getInconclusiveReason());
        if (llmResult.isOk()) { /* path=llm */ } else { /* path=fallback */ }
    }
    /* calibrate confidence, derive next_action, INSERT decisions, UPSERT container_states */
}
```

`buildSnapshots` is the subtle piece:

```java
Instant since = Instant.now().minusSeconds(60L * 24 * 3600);   // 60 days
var allEvents = normalized.findAllByContainerIdAndEventTimestampAfterOrderByEventTimestampAsc(containerId, since);
Map<UUID, List<NormalizedEventEntity>> bySource = ...;          // group by source

for (var s : enabled) {
    var list = bySource.getOrDefault(s.getId(), List.of());
    out.add(SourceSnapshot.builder()
        .responded(!list.isEmpty())
        .events(...)
        .build());
}
```

The router sees **all normalized events from the last 60 days for that
container**, not just the ones from the raw event that just arrived. A
source counts as "responded" if it has at least one event in that window.
This is why changing a stub does not erase history — the snapshot includes
all old events.

#### LLM path: `OpenAiTriageLlmClient.triage(...)`

If the rule is inconclusive, the LLM is invoked. Internally:

1. Check the `llm` circuit breaker (Resilience4j). Open → `not ok`.
2. Check the API key is not empty → if empty, `not ok` without calling
   OpenAI.
3. Build the prompt via `TriagePromptBuilder.build(snapshots, eta, reason)`.
4. POST to `https://api.openai.com/v1/chat/completions` with
   `response_format: json_object`, a fixed system prompt, and the prompt
   as the user message.
5. Submit to an executor and `Future.get(2 s, MILLISECONDS)` for the
   timeout.
6. Parse `{status, confidence, reasoning}` from the JSON response.
7. Persist everything in `llm_calls` with status `ok` / `timeout` / `error`
   / `circuit_open`.

If any step fails, `LlmTriageResult.notOk(...)` is returned and the
reconciliation service falls back.

#### Confidence calibration

`ConfidenceCalibrator.calibrate(baseConfidence, path, snapshots, now)`:

```java
double base = (path == DecisionPath.rule) ? Math.max(baseConfidence, 0.90) :
              (path == DecisionPath.llm)  ? 0.70 * baseConfidence :
                                            0.40;   // fallback
double calibrated = base * (0.5 + 0.25*agreement + 0.15*coverage + 0.10*freshness);
```

Three factors are mixed in:

- **agreement** — fraction of responding sources whose set of `EventType`s
  matches the first responder's.
- **coverage** — `responded / total_enabled_sources`.
- **freshness** — how recent the latest event is, linear 0..1 over a
  7-day window.

The `rule` path gets a floor of 0.90; the `llm` path is penalised to 70%
of self-report; the `fallback` path starts at 0.40.

#### Next action

`NextActionMapper.map(status, confidence, coverage)`:

```java
if (status == LOST)         return mark_lost;
if (status == NEEDS_REVIEW) return escalate_to_human;
if (confidence >= threshold) return wait;        // threshold default 0.80
return coverage < 1.0 ? refresh_source : escalate_to_human;
```

#### Persistence

```java
DecisionEntity decision = decisions.save(...);   // INSERT, append-only
upsertState(container, decision, status, calibrated, reasoning, next, path);
```

`decisions` is immutable. `container_states` is the materialized view of
the latest state (UPSERT). If a previous decision existed, a
`ContainerStateRevised` domain event is emitted.

```
ReconciliationWorker.tick() [500 ms]
  ├─ claimBatchSystemWide(25)               [FOR UPDATE SKIP LOCKED]
  └─ for each raw_event in batch:
       └─ processOne(ev)
            ├─ normalizer.normalize(ev)
            │    ├─ ContainerResolver.resolveOrCreate(...)
            │    ├─ MappingEngine.normalize(payload, mapping_json)
            │    └─ INSERT normalized_events
            ├─ ReconciliationService.reconcile(containerId)
            │    ├─ buildSnapshots(containerId)        [60-day history]
            │    ├─ RuleRouter.evaluate(snapshots, eta, now)
            │    ├─ if !decided: OpenAiTriageLlmClient.triage(...)
            │    ├─ ConfidenceCalibrator.calibrate(...)
            │    ├─ NextActionMapper.map(...)
            │    ├─ INSERT decisions
            │    └─ UPSERT container_states
            └─ UPDATE raw_events SET processing_status='processed'
```

### Flow 4 — Read: `GET /containers/{id}/status`

Independent of the above. Driven by HTTP request threads.

**`BearerAuthInterceptor` (preHandle):**

```java
String header = request.getHeader("Authorization");
if (!header.startsWith("Bearer ")) → 401;
String token = header.substring(7);
Optional<TenantEntity> t = tenants.findByApiTokenHash(hash(token));
if (t.isEmpty()) → 401;
TenantContext.set(t.get().getId());          // ThreadLocal
```

The `TenantContext` is a `ThreadLocal<UUID>` retrieved later via
`TenantContext.currentTenantId()`. `afterCompletion` clears it so it does
not leak across requests in the thread pool.

**`ContainerStatusController.status(businessId)`:**

```java
UUID tenantId = TenantContext.currentTenantId();
var container = containers.findByTenantIdAndContainerBusinessId(tenantId, businessId);
if (container == null) → 404;

var state = states.findByTenantIdAndContainerId(tenantId, container.getId());
if (state == null) → 404;

if (Duration.between(state.getLastSourceRefreshAt(), Instant.now()).toMinutes() >= ttlMinutes) {
    refresh.refreshWithin(Duration.ofMillis(refreshBudgetMs), tenantId, businessId);
    state = states.findByTenantIdAndContainerId(...).orElse(state);
}
return toDto(state);
```

If the last refresh is older than
`triage.default-source-freshness-ttl-minutes` (default 15), an on-demand
refresh is attempted with a hard budget (default 1 s):

```java
var pollingSources = sources.findAllByEnabledTrueAndPollingIntervalSecIsNotNull();
Future<?> future = exec.submit(() -> {
    for (var src : pollingSources) {
        connector.fetch(businessId, cfg).forEach(p -> ingestion.ingest(...));
    }
});
try { TimeLimiter.of(budget).executeFutureSupplier(future); }
catch (Exception ignored) { future.cancel(true); }
```

This is the **stale-while-revalidate** pattern. The controller serves
whatever is in `container_states` right now, and best-effort refreshes in
background. If the sources respond within the budget, the raw events join
the queue and the worker will reconcile them on the next 500 ms tick. If
not, the cached state is returned anyway — the endpoint never hangs.

---

## Threading model

```
scheduling-1 (timer thread):
  ├─ PollingScheduler.tick()         every 1 s
  └─ ReconciliationWorker.tick()     every 500 ms

http-nio-* (Tomcat workers):
  ├─ WebhookController.webhook()
  ├─ ContainerStatusController.status()
  └─ all /admin/* controllers

polling-scheduler-N (cached pool):
  └─ connector.fetch(...) per subscription

llm-triage (cached pool):
  └─ OpenAI HTTP call

refresh-orchestrator (cached pool):
  └─ on-demand fetch from GET /containers
```

`@Scheduled` tasks are serialized in the default pool, so the polling and
reconciliation ticks never overlap with themselves. Heavy I/O (HTTP calls)
is delegated to dedicated cached pools so a slow source cannot stall the
scheduler.

---

## Failure modes and how they are handled

| Failure                                | Where it is handled                                    | Resulting behaviour                                                          |
| -------------------------------------- | ------------------------------------------------------ | ---------------------------------------------------------------------------- |
| Source HTTP 503 / timeout              | `pollOne` catch → `breaker.onError`                    | Warning logged, no `raw_event` created                                       |
| Source failing ≥ 50% in 20 calls       | Resilience4j `source-{name}` opens                     | Following 30 s skip the HTTP call entirely                                   |
| WireMock returns invalid JSON          | `MappingEngine` throws `MappingError`                  | `raw_event` stays `pending` + attempts++; after 3, marked `failed_processing`|
| OpenAI slow                            | `Future.get(2000ms, MILLISECONDS)`                     | `LlmTriageResult.notOk("timeout")` → fallback path                           |
| OpenAI 5xx                             | catch in `OpenAiTriageLlmClient`                       | `breaker.onError` + fallback path                                            |
| OpenAI breaker open                    | `breaker.tryAcquirePermission() == false`              | `llm_calls(status='circuit_open')` persisted + fallback path                 |
| Worker transaction fails mid-way       | `txTemplate.executeWithoutResult` rolls back           | `raw_event` keeps `attempts++`, will be retried                              |
| Unknown container in a webhook         | `ContainerResolver.resolveOrCreate(...)`               | Container is created on the fly with just its business id                    |
| Invalid bearer token                   | `BearerAuthInterceptor`                                | 401 before the controller is reached                                         |
| Stale state on `GET status`            | `RefreshOrchestrator` with 1 s budget                  | Cache is served if the refresh does not complete in time                     |
| Multiple workers race on `raw_events`  | `FOR UPDATE SKIP LOCKED` in claim                      | Each worker takes a disjoint batch                                           |

---

## Runtime configuration

| Property                                                | Default       | Where it is read                                                       |
| ------------------------------------------------------- | ------------- | ---------------------------------------------------------------------- |
| `triage.auto-resolve-threshold`                         | 0.80          | `NextActionMapper` constructor                                         |
| `triage.default-source-freshness-ttl-minutes`           | 15            | `ContainerStatusController` constructor                                |
| `triage.reconciliation.worker-poll-interval-ms`         | 500           | `@Scheduled` on `ReconciliationWorker`                                 |
| `triage.reconciliation.worker-batch-size`               | 25            | `ReconciliationWorker` constructor                                     |
| `triage.reconciliation.worker-enabled`                  | true          | `@ConditionalOnProperty` on the worker (off for tests)                 |
| `triage.llm.enabled`                                    | true          | `@ConditionalOnProperty` on `OpenAiTriageLlmClient`                    |
| `triage.llm.model`                                      | `gpt-4o-mini` | env `OPENAI_MODEL`                                                     |
| `triage.llm.timeout-ms`                                 | 2000          | `Future.get(timeout, MS)` in the LLM client                            |
| `triage.llm.api-key`                                    | (empty)       | env `OPENAI_API_KEY`                                                   |
| `triage.simulators.*`                                   | seeded yml    | `SimulatorRegistry` (admin proxy for WireMock)                         |
| `resilience4j.circuitbreaker.configs.source.*`          | window 20, 50% threshold | `pollOne` via name `source-{name}`                          |
| `resilience4j.circuitbreaker.instances.llm.*`           | window 30, min 5, 50%    | `OpenAiTriageLlmClient` constructor                         |

---

## SPA pages reference

URL: <http://localhost:13000>

### Header (all pages)
A tenant dropdown. The selection is persisted in `localStorage`. Most pages
need a tenant selected because they send `X-Tenant-Id` with every admin
request.

### `/dashboard` — containers table
Lists all containers for the selected tenant with `business_id` (link to
detail), coloured status badge, confidence, next action, ETA, last update.
The table auto-refreshes every 5 s. **+ New container** opens the create
form.

### `/containers/new` — create container
Form: `business_id` (unique per tenant), carrier, POL, POD, declared ETA.
Creates the row in `containers` only — the container will not receive
events until it is subscribed to at least one source.

### `/containers/:businessId` — detail
Three blocks:
1. Header: business id, route (POL → POD), ETA, current status badge.
2. Current state (if any): reasoning, confidence, next action, decision
   path, last update.
3. Tabs: **Decisions** (chronological timeline of every decision, with the
   path, status badge, reasoning, latency) and **Raw events** (the original
   JSON each simulator returned). Auto-refreshes every 5 s. Delete button
   cascades to decisions, normalized events, and raw events.

### `/sources` — list sources
Table with name, connector type, enabled toggle (with a confirm dialog),
polling interval, supports-webhook flag, subscription count.

### `/sources/new` — create source
Form with: name, connector type (dropdown of `carrier_portal`,
`terminal_feed`, `partner_webhook`), polling interval, flags, and live
JSON editors for `config_json` and `mapping_json`. Invalid JSON blocks
the save with an inline error.

### `/sources/:id` — edit source
Three tabs: **Config** (polling interval, `config_json`),
**Mapping** (`mapping_json`), **Subscriptions** (add/remove
`{tenant_id, container_id}` from `config_json.subscriptions`). The polling
scheduler reads that array every tick, so subscription changes take effect
on the next interval boundary without a restart.

### `/stubs` — index of simulators
One card per configured simulator (read from `triage.simulators`). Each
card shows an `online/offline` badge from probing `__admin/mappings`.

### `/stubs/:sourceName` — WireMock stub editor
Lists current stubs as cards with editable JSON bodies; an **+ Add stub**
form on top. Save sends `PUT /admin/simulators/{name}/stubs/{containerId}`,
which deletes any existing mapping for the same container URL and posts a
new one to WireMock. Changes take effect immediately; the next polling tick
will fetch the new response.

### `/tenants` — tenants list
Shows name, id, demo bearer token (only for the seeded tenants — others
return null because only the hash is persisted), and a **Use** button that
sets the tenant as the global context.

---

## Tracing an event end to end

When something looks wrong in the UI, follow the chain via SQL:

```sql
-- 1) Did anything raw arrive?
SELECT id, source_id, received_at, processing_status, processing_attempts, last_error
FROM raw_events
WHERE container_business_id = 'MSCU-XYZ' AND tenant_id = '<uuid>'
ORDER BY received_at DESC LIMIT 5;

-- 2) Was it normalized?
SELECT ne.event_type, ne.event_timestamp, ne.source_id
FROM normalized_events ne
JOIN containers c ON c.id = ne.container_id
WHERE c.container_business_id = 'MSCU-XYZ' AND c.tenant_id = '<uuid>'
ORDER BY ne.normalized_at DESC LIMIT 10;

-- 3) What decisions were taken?
SELECT decided_at, path, status, confidence, reasoning, rules_fired_json
FROM decisions
WHERE container_id = (SELECT id FROM containers WHERE container_business_id='MSCU-XYZ')
ORDER BY decided_at DESC LIMIT 5;

-- 4) If it went to the LLM, what happened?
SELECT called_at, status, latency_ms, response_json
FROM llm_calls
ORDER BY called_at DESC LIMIT 5;

-- 5) What is the current state?
SELECT status, confidence, next_action, decided_by_path, last_source_refresh_at, updated_at
FROM container_states cs
JOIN containers c ON c.id = cs.container_id
WHERE c.container_business_id = 'MSCU-XYZ';
```

Each step shows where the chain broke.

---

## Tests

```bash
./mvnw verify
```

Runs unit tests plus Testcontainers-backed integration tests covering:
end-to-end happy path, tenant isolation, resilience when a source is down,
LLM fallback when the LLM client throws, deterministic rule outcomes for
each of R1..R7, audit row persistence and supersession, mapping-driven
normalization, connector registry, polling scheduler, webhook ingestion,
HTTP auth, admin CRUD endpoints, and the WireMock proxy used by the stub
editor.

---

## External sources and demo dataset

Three connectors are registered:

- `carrier-portal-v1` — JSON over HTTP, pull, the authoritative source.
- `terminal-feed-v1` — JSON over HTTP, pull, sometimes lags or flakes.
- `partner-webhook-v1` — HMAC-signed webhook receiver; the push contract.
  Not exercised by the demo dataset, but the abstraction is in place.

Seven demo containers in `V3__seed_demo.sql`, one case per rule path:

| Container             | Tenant   | Case                                                                       | Expected path / status                                                  |
| --------------------- | -------- | -------------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| `MSCU-HAPPY`          | tenant-a | Both sources agree on `gate_out` + `sail`, ETA in future                   | `rule` R2 → `ON_TRACK`                                                  |
| `MSCU-DELAYED`        | tenant-a | Both sources agree on `sail`, ETA in the past (no arrival)                 | `rule` R3 → `DELAYED`                                                   |
| `MSCU-LISBOA-NOTE`    | tenant-a | Sources agree on `sail`, ETA in future. Carrier note: "Vessel delayed at LISBOA, ETA may shift" | `rule` R2 → `ON_TRACK` (rules); `llm` → `DELAYED` (when key set) |
| `MSCU-STALE`          | tenant-a | Both show `sail` ~5 months ago, no arrival                                 | R1 inconclusive → `fallback` → `NEEDS_REVIEW` (LLM may classify `LOST`) |
| `MSCU-CONFLICT`       | tenant-a | Carrier `sail` 2026-05-01, terminal `sail` 2026-05-02 (> 6 h apart)        | R5 inconclusive → `fallback` → `NEEDS_REVIEW`                           |
| `MSCU-FLAKY`          | tenant-a | Carrier responds normally; terminal returns HTTP 503                       | Coverage 1/2 → R6 inconclusive → `fallback` → `NEEDS_REVIEW`            |
| `MSCU-HAPPY`          | tenant-b | Same business id as tenant-a but a different tenant — isolated history     | Independent row in `container_states`                                   |

The WireMock stubs live under `simulators/{carrier-portal,terminal-feed}/mappings/`.

---

## Design decisions

1. **Confidence threshold for auto-escalation: 0.80**, configurable via
   `triage.auto-resolve-threshold`. Below threshold the decision still
   produces a `status` but the `next_action` becomes `refresh_source` or
   `escalate_to_human`. A false-positive `ON_TRACK` costs the customer's
   trust; an extra ops review costs minutes.

2. **Freshness: TTL-based, per-source, default 15 min.** Background
   ingestion keeps state current; on a stale read the query path triggers
   an on-demand refresh with a 1 s sync budget and serves the stale view if
   the budget is missed (honest about which view was served).

3. **Conflicting source reports: honesty over staleness.** If sources
   disagree past the 6 h tolerance and rules cannot reconcile, the LLM is
   consulted; if it cannot confidently resolve, `NEEDS_REVIEW` is emitted.
   Silently picking the older agreed answer would mask the exact signal
   ops needs.

4. **Rollback of false-positives: immutable audit, supersede.** A
   contradicting later event writes a *new* `decisions` row, updates
   `container_states` with a version bump, links the previous decision via
   `superseded_by`, and emits a `ContainerStateRevised` domain event.
   History is never rewritten.

5. **The LLM is never on the critical path.** The query endpoint reads
   `container_states` directly — no synchronous LLM calls. The
   reconciliation worker calls the LLM once, with a hard timeout and a
   circuit breaker, and falls back to `NEEDS_REVIEW` if anything goes
   wrong. The service degrades gracefully without OpenAI.

6. **Mapping is configuration, not code.** A new source with a different
   JSON shape is added by inserting a row in `sources` with a custom
   `mapping_json`. No Java change is required as long as the connector
   protocol (HTTP GET / webhook) is one of the three already supported.

7. **Postgres as a queue, for now.** `FOR UPDATE SKIP LOCKED` gives
   horizontal scalability for the worker without external infrastructure.
   The `RawEventRepository.claimBatchSystemWide` query is the only seam
   that would change for a Kafka migration.

---

## Known limitations (MVP)

- `/admin/*` endpoints are unauthenticated. Production usage would gate
  them behind a real admin role.
- Triage knobs (`triage.auto-resolve-threshold`, freshness TTL) stay
  env-driven; the UI does not edit them live.
- No global audit viewer for `raw_events` / `llm_calls`; per-container
  events are visible in the container detail page.
- No automated frontend tests.
- Stubs created via the editor share the simulator state with the file-based
  stubs that ship in `simulators/*/mappings/`. Editing a container's stub
  replaces the matching URL mapping; deleting a container does not restore
  the original file-based mapping until the WireMock container restarts.
