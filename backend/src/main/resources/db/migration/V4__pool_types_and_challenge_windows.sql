-- First-class recurring leagues and repeatable challenge occurrences.

ALTER TABLE arena_pools
    ADD COLUMN pool_type VARCHAR(16) NOT NULL DEFAULT 'POOL';

ALTER TABLE arena_pools
    ADD COLUMN recurring BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE arena_pools
SET pool_type = 'LEAGUE', recurring = TRUE
WHERE invite_code = 'ARENA26' OR LOWER(name) = 'liga arena 2026';

ALTER TABLE arena_pools
    ADD CONSTRAINT ck_arena_pools_type
    CHECK (pool_type IN ('POOL', 'LEAGUE'));

ALTER TABLE arena_user_challenges
    ADD COLUMN window_start TIMESTAMP WITH TIME ZONE;

ALTER TABLE arena_user_challenges
    ADD COLUMN window_end TIMESTAMP WITH TIME ZONE;

UPDATE arena_user_challenges state
SET window_start = (SELECT definition.starts_at FROM arena_challenge_definitions definition WHERE definition.id = state.challenge_id),
    window_end = (SELECT definition.expires_at FROM arena_challenge_definitions definition WHERE definition.id = state.challenge_id);

