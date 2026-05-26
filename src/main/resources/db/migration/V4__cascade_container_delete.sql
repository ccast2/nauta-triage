-- V4: enable ON DELETE CASCADE on FKs that reference containers.
-- Without this, deleting a container fails because raw_events / normalized_events /
-- decisions / container_states still reference it.

ALTER TABLE raw_events
    DROP CONSTRAINT IF EXISTS raw_events_container_id_ref_fkey,
    ADD  CONSTRAINT raw_events_container_id_ref_fkey
         FOREIGN KEY (container_id_ref) REFERENCES containers(id) ON DELETE CASCADE;

ALTER TABLE normalized_events
    DROP CONSTRAINT IF EXISTS normalized_events_container_id_fkey,
    ADD  CONSTRAINT normalized_events_container_id_fkey
         FOREIGN KEY (container_id) REFERENCES containers(id) ON DELETE CASCADE;

ALTER TABLE decisions
    DROP CONSTRAINT IF EXISTS decisions_container_id_fkey,
    ADD  CONSTRAINT decisions_container_id_fkey
         FOREIGN KEY (container_id) REFERENCES containers(id) ON DELETE CASCADE;

ALTER TABLE container_states
    DROP CONSTRAINT IF EXISTS container_states_container_id_fkey,
    ADD  CONSTRAINT container_states_container_id_fkey
         FOREIGN KEY (container_id) REFERENCES containers(id) ON DELETE CASCADE;
