-- Harden the ArenaPredict V1 core without changing the legacy Copa tables.
-- Constraint names are intentionally stable: Flyway executes this version once,
-- and both PostgreSQL and H2 support the portable ADD CONSTRAINT syntax below.

-- Relational integrity -------------------------------------------------------

ALTER TABLE arena_championships
    ADD CONSTRAINT fk_arena_championships_sport
    FOREIGN KEY (sport_id) REFERENCES arena_sports(id);

ALTER TABLE arena_competitors
    ADD CONSTRAINT fk_arena_competitors_sport
    FOREIGN KEY (sport_id) REFERENCES arena_sports(id);

ALTER TABLE arena_events
    ADD CONSTRAINT fk_arena_events_championship
    FOREIGN KEY (championship_id) REFERENCES arena_championships(id);

ALTER TABLE arena_events
    ADD CONSTRAINT fk_arena_events_home_competitor
    FOREIGN KEY (home_competitor_id) REFERENCES arena_competitors(id);

ALTER TABLE arena_events
    ADD CONSTRAINT fk_arena_events_away_competitor
    FOREIGN KEY (away_competitor_id) REFERENCES arena_competitors(id);

ALTER TABLE arena_markets
    ADD CONSTRAINT fk_arena_markets_event
    FOREIGN KEY (event_id) REFERENCES arena_events(id);

ALTER TABLE arena_market_options
    ADD CONSTRAINT fk_arena_market_options_market
    FOREIGN KEY (market_id) REFERENCES arena_markets(id);

ALTER TABLE arena_point_wallets
    ADD CONSTRAINT fk_arena_point_wallets_user
    FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE arena_point_transactions
    ADD CONSTRAINT fk_arena_point_transactions_wallet
    FOREIGN KEY (wallet_id) REFERENCES arena_point_wallets(id);

ALTER TABLE arena_pools
    ADD CONSTRAINT fk_arena_pools_sport
    FOREIGN KEY (sport_id) REFERENCES arena_sports(id);

ALTER TABLE arena_pools
    ADD CONSTRAINT fk_arena_pools_championship
    FOREIGN KEY (championship_id) REFERENCES arena_championships(id);

ALTER TABLE arena_pools
    ADD CONSTRAINT fk_arena_pools_owner
    FOREIGN KEY (owner_id) REFERENCES users(id);

ALTER TABLE arena_pool_members
    ADD CONSTRAINT fk_arena_pool_members_pool
    FOREIGN KEY (pool_id) REFERENCES arena_pools(id);

ALTER TABLE arena_pool_members
    ADD CONSTRAINT fk_arena_pool_members_user
    FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE arena_predictions
    ADD CONSTRAINT fk_arena_predictions_user
    FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE arena_predictions
    ADD CONSTRAINT fk_arena_predictions_event
    FOREIGN KEY (event_id) REFERENCES arena_events(id);

ALTER TABLE arena_predictions
    ADD CONSTRAINT fk_arena_predictions_market
    FOREIGN KEY (market_id) REFERENCES arena_markets(id);

ALTER TABLE arena_predictions
    ADD CONSTRAINT fk_arena_predictions_option
    FOREIGN KEY (option_id) REFERENCES arena_market_options(id);

ALTER TABLE arena_predictions
    ADD CONSTRAINT fk_arena_predictions_pool
    FOREIGN KEY (pool_id) REFERENCES arena_pools(id);

ALTER TABLE arena_notifications
    ADD CONSTRAINT fk_arena_notifications_user
    FOREIGN KEY (user_id) REFERENCES users(id);

-- Domain invariants ----------------------------------------------------------

ALTER TABLE arena_events
    ADD CONSTRAINT ck_arena_events_best_of
    CHECK (best_of IN (1, 3, 5));

ALTER TABLE arena_events
    ADD CONSTRAINT ck_arena_events_home_score_nonnegative
    CHECK (home_score IS NULL OR home_score >= 0);

ALTER TABLE arena_events
    ADD CONSTRAINT ck_arena_events_away_score_nonnegative
    CHECK (away_score IS NULL OR away_score >= 0);

ALTER TABLE arena_markets
    ADD CONSTRAINT ck_arena_markets_minimum_points_positive
    CHECK (minimum_points > 0);

ALTER TABLE arena_market_options
    ADD CONSTRAINT ck_arena_market_options_multiplier_gt_one
    CHECK (multiplier > 1);

ALTER TABLE arena_point_wallets
    ADD CONSTRAINT ck_arena_point_wallets_balance_nonnegative
    CHECK (balance >= 0);

ALTER TABLE arena_point_wallets
    ADD CONSTRAINT ck_arena_point_wallets_lifetime_earned_nonnegative
    CHECK (lifetime_earned >= 0);

ALTER TABLE arena_point_wallets
    ADD CONSTRAINT ck_arena_point_wallets_lifetime_used_nonnegative
    CHECK (lifetime_used >= 0);

ALTER TABLE arena_predictions
    ADD CONSTRAINT ck_arena_predictions_stake_nonnegative
    CHECK (stake_points >= 0);

ALTER TABLE arena_predictions
    ADD CONSTRAINT ck_arena_predictions_multiplier_gt_one
    CHECK (multiplier > 1);

ALTER TABLE arena_predictions
    ADD CONSTRAINT ck_arena_predictions_potential_nonnegative
    CHECK (potential_points >= 0);

ALTER TABLE arena_predictions
    ADD CONSTRAINT ck_arena_predictions_reward_nonnegative
    CHECK (rewarded_points >= 0);

-- Missing FK indexes. Existing unique/composite indexes already cover
-- championships.sport_id, competitors.sport_id, markets.event_id,
-- market_options.market_id, wallets.user_id, pool_members.pool_id,
-- predictions.user_id/market_id, transactions.wallet_id and notifications.user_id.

CREATE INDEX IF NOT EXISTS idx_arena_events_championship_fk
    ON arena_events(championship_id);
CREATE INDEX IF NOT EXISTS idx_arena_events_home_competitor_fk
    ON arena_events(home_competitor_id);
CREATE INDEX IF NOT EXISTS idx_arena_events_away_competitor_fk
    ON arena_events(away_competitor_id);
CREATE INDEX IF NOT EXISTS idx_arena_pools_sport_fk
    ON arena_pools(sport_id);
CREATE INDEX IF NOT EXISTS idx_arena_pools_championship_fk
    ON arena_pools(championship_id);
CREATE INDEX IF NOT EXISTS idx_arena_pools_owner_fk
    ON arena_pools(owner_id);
CREATE INDEX IF NOT EXISTS idx_arena_pool_members_user_fk
    ON arena_pool_members(user_id);
CREATE INDEX IF NOT EXISTS idx_arena_predictions_event_fk
    ON arena_predictions(event_id);
CREATE INDEX IF NOT EXISTS idx_arena_predictions_option_fk
    ON arena_predictions(option_id);
CREATE INDEX IF NOT EXISTS idx_arena_predictions_pool_fk
    ON arena_predictions(pool_id);
