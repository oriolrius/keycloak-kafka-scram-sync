-- Add retry_count column to sync_operation table
-- Tracks number of retry attempts for each operation

ALTER TABLE sync_operation ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0;

CREATE INDEX idx_sync_operation_retry ON sync_operation(retry_count);
