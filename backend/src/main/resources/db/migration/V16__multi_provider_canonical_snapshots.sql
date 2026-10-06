ALTER TABLE arena_events ADD COLUMN source_status VARCHAR(40);
ALTER TABLE arena_events ADD COLUMN source_snapshot_hash VARCHAR(64);
ALTER TABLE arena_events ADD COLUMN source_metrics VARCHAR(4000);
ALTER TABLE arena_events ADD COLUMN data_quality VARCHAR(20);
ALTER TABLE arena_predictions ADD COLUMN multiplier_origin VARCHAR(24) NOT NULL DEFAULT 'ADMIN_DEFINED';
ALTER TABLE arena_predictions ADD COLUMN multiplier_model_version VARCHAR(50);
INSERT INTO arena_sports_sync_state(provider) VALUES ('API_FOOTBALL'),('API_BASKETBALL'),('API_TENNIS'),('API_FORMULA1');
