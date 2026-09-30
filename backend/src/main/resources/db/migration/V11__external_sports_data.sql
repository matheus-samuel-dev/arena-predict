-- Add provider identity without rewriting existing demo/administrative data.
ALTER TABLE arena_events ADD COLUMN external_provider VARCHAR(40);
ALTER TABLE arena_events ADD COLUMN external_id VARCHAR(100);
ALTER TABLE arena_events ADD COLUMN last_synced_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE arena_events ADD COLUMN finished_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE arena_events ADD COLUMN result_processed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE arena_events ADD COLUMN winner_external_id VARCHAR(100);
ALTER TABLE arena_events ADD COLUMN live_score_available BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE arena_events ADD COLUMN result_fingerprint VARCHAR(64);
ALTER TABLE arena_events ADD COLUMN pending_result_data VARCHAR(4000);
ALTER TABLE arena_events ADD COLUMN result_review_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE arena_events ALTER COLUMN best_of DROP NOT NULL;
ALTER TABLE arena_events ADD CONSTRAINT uq_arena_event_provider UNIQUE(external_provider, external_id);
ALTER TABLE arena_events ADD CONSTRAINT ck_arena_event_provider CHECK (
    (external_provider IS NULL AND external_id IS NULL) OR
    (external_provider IS NOT NULL AND external_id IS NOT NULL AND demo = FALSE));
CREATE INDEX idx_arena_external_events_schedule ON arena_events(external_provider, status, starts_at);

ALTER TABLE arena_competitors ADD COLUMN external_provider VARCHAR(40);
ALTER TABLE arena_competitors ADD COLUMN external_id VARCHAR(100);
ALTER TABLE arena_competitors ADD COLUMN acronym VARCHAR(40);
ALTER TABLE arena_competitors ALTER COLUMN image_url TYPE VARCHAR(2048);
ALTER TABLE arena_competitors ADD CONSTRAINT uq_arena_competitor_provider UNIQUE(external_provider, external_id);

ALTER TABLE arena_championships ADD COLUMN external_provider VARCHAR(40);
ALTER TABLE arena_championships ADD COLUMN external_id VARCHAR(100);
ALTER TABLE arena_championships ADD COLUMN league_name VARCHAR(180);
ALTER TABLE arena_championships ADD COLUMN series_name VARCHAR(180);
ALTER TABLE arena_championships ALTER COLUMN season DROP NOT NULL;
ALTER TABLE arena_championships ALTER COLUMN image_url TYPE VARCHAR(2048);
ALTER TABLE arena_championships ADD CONSTRAINT uq_arena_championship_provider UNIQUE(external_provider, external_id);

-- A durable lease coordinates replicas; no network call holds a DB transaction.
CREATE TABLE arena_sports_sync_state (
    provider VARCHAR(40) PRIMARY KEY,
    lease_owner VARCHAR(100),
    lease_until TIMESTAMP WITH TIME ZONE,
    last_attempt_at TIMESTAMP WITH TIME ZONE,
    last_success_at TIMESTAMP WITH TIME ZONE,
    upcoming_at TIMESTAMP WITH TIME ZONE,
    running_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    tracked_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(32) NOT NULL DEFAULT 'UNCONFIGURED',
    message VARCHAR(300)
);
INSERT INTO arena_sports_sync_state(provider) VALUES ('PANDASCORE');
