-- Persist the social sandbox boundary; never infer it from user-submitted request flags.
ALTER TABLE arena_community_posts ADD COLUMN demo BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE arena_community_posts SET demo = TRUE WHERE source_key IS NOT NULL;
CREATE INDEX idx_community_demo_feed ON arena_community_posts (demo, status, created_at);
