CREATE TABLE arena_sports_history_backfill (
    provider VARCHAR(64) NOT NULL,
    sport VARCHAR(40) NOT NULL,
    target_at TIMESTAMP WITH TIME ZONE NOT NULL,
    window_end_at TIMESTAMP WITH TIME ZONE NOT NULL,
    next_page INTEGER NOT NULL DEFAULT 1 CHECK (next_page > 0),
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    last_attempt_at TIMESTAMP WITH TIME ZONE,
    last_success_at TIMESTAMP WITH TIME ZONE,
    received_count BIGINT NOT NULL DEFAULT 0,
    accepted_count BIGINT NOT NULL DEFAULT 0,
    rejected_count BIGINT NOT NULL DEFAULT 0,
    last_page_fingerprint VARCHAR(64),
    last_error VARCHAR(64),
    PRIMARY KEY (provider, sport),
    CHECK (window_end_at >= target_at)
);

-- Statistical evidence only: never creates markets, predictions, wallets or historical payouts.
CREATE TABLE arena_sports_results_history (
    provider VARCHAR(64) NOT NULL,
    external_id VARCHAR(64) NOT NULL,
    sport VARCHAR(40) NOT NULL,
    home_id VARCHAR(64) NOT NULL,
    away_id VARCHAR(64) NOT NULL,
    championship_external_id VARCHAR(64) NOT NULL,
    home_score INTEGER NOT NULL,
    away_score INTEGER NOT NULL,
    best_of INTEGER NOT NULL CHECK (best_of IN (1,3,5)),
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITH TIME ZONE NOT NULL,
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    PRIMARY KEY (provider, external_id),
    CHECK (home_id <> away_id),
    CHECK (starts_at <= ended_at),
    CHECK (home_score >= 0 AND away_score >= 0),
    CHECK (GREATEST(home_score,away_score) = best_of/2+1),
    CHECK (LEAST(home_score,away_score) < best_of/2+1)
);
CREATE INDEX idx_sports_history_recent ON arena_sports_results_history(provider, sport, ended_at DESC);
