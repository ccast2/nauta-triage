-- V3__seed_demo.sql
-- Demo dataset for the Container Status Triage Service challenge.
--
-- Bearer tokens (documented in README.md):
--   tenant-a -> "demo-token-a"
--     SHA-256 = 5307bdd134e2317a34aa9bbf9ec1c1971a3314ac2069e8d869ad80088abcbe82
--   tenant-b -> "demo-token-b"
--     SHA-256 = 083bc230ce904b8d38735f47857e93be5ad886b1863ba4fbd223038c94a6c0d8
--
-- Idempotent so it is safe to re-run against a partially-seeded DB and so it
-- does not collide with rows created by integration tests (which use their
-- own unique names / token hashes).

INSERT INTO tenants (id, name, api_token_hash) VALUES
  ('11111111-1111-1111-1111-111111111111', 'tenant-a',
   '5307bdd134e2317a34aa9bbf9ec1c1971a3314ac2069e8d869ad80088abcbe82'),
  ('22222222-2222-2222-2222-222222222222', 'tenant-b',
   '083bc230ce904b8d38735f47857e93be5ad886b1863ba4fbd223038c94a6c0d8')
ON CONFLICT (id) DO NOTHING;

INSERT INTO sources (id, name, connector_type, config_json, mapping_json, enabled, polling_interval_sec, supports_webhook) VALUES
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'carrier-portal-demo', 'carrier-portal-v1',
   ('{"base_url":"http://carrier-portal:8080","subscriptions":['
   || '{"tenant_id":"11111111-1111-1111-1111-111111111111","container_id":"MSCU-HAPPY"},'
   || '{"tenant_id":"11111111-1111-1111-1111-111111111111","container_id":"MSCU-DELAYED"},'
   || '{"tenant_id":"11111111-1111-1111-1111-111111111111","container_id":"MSCU-LISBOA-NOTE"},'
   || '{"tenant_id":"11111111-1111-1111-1111-111111111111","container_id":"MSCU-STALE"},'
   || '{"tenant_id":"11111111-1111-1111-1111-111111111111","container_id":"MSCU-CONFLICT"},'
   || '{"tenant_id":"11111111-1111-1111-1111-111111111111","container_id":"MSCU-FLAKY"},'
   || '{"tenant_id":"22222222-2222-2222-2222-222222222222","container_id":"MSCU-HAPPY"}'
   || ']}')::jsonb,
   '{"container_id_path":"$.container_id","events_path":"$.events","event_type_path":"$.type","event_timestamp_path":"$.date","eta_path":"$.eta","note_path":"$.note"}'::jsonb,
   true, 10, false),
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'terminal-feed-demo', 'terminal-feed-v1',
   ('{"base_url":"http://terminal-feed:8080","subscriptions":['
   || '{"tenant_id":"11111111-1111-1111-1111-111111111111","container_id":"MSCU-HAPPY"},'
   || '{"tenant_id":"11111111-1111-1111-1111-111111111111","container_id":"MSCU-DELAYED"},'
   || '{"tenant_id":"11111111-1111-1111-1111-111111111111","container_id":"MSCU-LISBOA-NOTE"},'
   || '{"tenant_id":"11111111-1111-1111-1111-111111111111","container_id":"MSCU-STALE"},'
   || '{"tenant_id":"11111111-1111-1111-1111-111111111111","container_id":"MSCU-CONFLICT"},'
   || '{"tenant_id":"11111111-1111-1111-1111-111111111111","container_id":"MSCU-FLAKY"},'
   || '{"tenant_id":"22222222-2222-2222-2222-222222222222","container_id":"MSCU-HAPPY"}'
   || ']}')::jsonb,
   '{"container_id_path":"$.container_id","events_path":"$.events","event_type_path":"$.type","event_timestamp_path":"$.date","eta_path":"$.eta"}'::jsonb,
   true, 10, false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO containers (tenant_id, container_business_id, carrier, pol, pod, declared_eta) VALUES
  ('11111111-1111-1111-1111-111111111111','MSCU-HAPPY',        'MSC','BRSSZ','USNYC','2026-06-15'),
  ('11111111-1111-1111-1111-111111111111','MSCU-DELAYED',      'MSC','BRSSZ','USNYC','2026-03-01'),
  ('11111111-1111-1111-1111-111111111111','MSCU-LISBOA-NOTE',  'MSC','BRSSZ','PTLIS','2026-05-30'),
  ('11111111-1111-1111-1111-111111111111','MSCU-STALE',        'MSC','BRSSZ','USNYC','2026-04-01'),
  ('11111111-1111-1111-1111-111111111111','MSCU-CONFLICT',     'MSC','BRSSZ','USNYC','2026-06-01'),
  ('11111111-1111-1111-1111-111111111111','MSCU-FLAKY',        'MSC','BRSSZ','USNYC','2026-06-10'),
  ('22222222-2222-2222-2222-222222222222','MSCU-HAPPY',        'HMM','KRPUS','USLAX','2026-07-01')
ON CONFLICT (tenant_id, container_business_id) DO NOTHING;
