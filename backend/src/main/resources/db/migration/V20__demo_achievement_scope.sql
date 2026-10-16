-- Existing achievements remain available to their existing audience.
ALTER TABLE arena_achievement_definitions ADD COLUMN demo_only BOOLEAN NOT NULL DEFAULT FALSE;
