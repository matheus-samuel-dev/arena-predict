ALTER TABLE arena_markets ADD COLUMN pricing_snapshot TEXT;
ALTER TABLE arena_predictions ADD COLUMN pricing_snapshot TEXT;
ALTER TABLE demo_training_predictions ADD COLUMN pricing_snapshot TEXT;
