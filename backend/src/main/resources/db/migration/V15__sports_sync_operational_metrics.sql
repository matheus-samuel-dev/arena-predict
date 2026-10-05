-- Safe operational metadata only. Credentials and provider response bodies are never persisted here.
ALTER TABLE arena_sports_sync_state ADD COLUMN scheduler_tick_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE arena_sports_sync_state ADD COLUMN run_completed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE arena_sports_sync_state ADD COLUMN retry_after_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE arena_sports_sync_state ADD COLUMN received_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE arena_sports_sync_state ADD COLUMN inserted_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE arena_sports_sync_state ADD COLUMN updated_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE arena_sports_sync_state ADD COLUMN skipped_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE arena_sports_sync_state ADD COLUMN failed_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE arena_sports_sync_state ADD COLUMN duration_ms BIGINT;
ALTER TABLE arena_sports_sync_state ADD COLUMN last_http_status INTEGER;
ALTER TABLE arena_sports_sync_state ADD COLUMN last_error_reason VARCHAR(40);
