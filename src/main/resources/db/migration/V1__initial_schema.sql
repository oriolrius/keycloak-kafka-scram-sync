-- Initial schema for keycloak-kafka-sync-agent
-- Based on Technical Analysis decision-001

-- Table: sync_operation
-- Stores individual synchronization operations between Keycloak and Kafka
CREATE TABLE sync_operation (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  correlation_id TEXT NOT NULL,
  occurred_at DATETIME NOT NULL,
  realm TEXT NOT NULL,
  cluster_id TEXT NOT NULL,
  principal TEXT NOT NULL,
  op_type TEXT NOT NULL,
  mechanism TEXT,
  result TEXT NOT NULL,
  error_code TEXT,
  error_message TEXT,
  duration_ms INTEGER NOT NULL
);

CREATE INDEX idx_sync_operation_time ON sync_operation(occurred_at);
CREATE INDEX idx_sync_operation_principal ON sync_operation(principal);
CREATE INDEX idx_sync_operation_type ON sync_operation(op_type);

-- Table: sync_batch
-- Stores batch synchronization metadata (reconciliation cycles)
CREATE TABLE sync_batch (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  correlation_id TEXT NOT NULL UNIQUE,
  started_at DATETIME NOT NULL,
  finished_at DATETIME,
  source TEXT NOT NULL,
  items_total INTEGER NOT NULL,
  items_success INTEGER NOT NULL DEFAULT 0,
  items_error INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_sync_batch_time ON sync_batch(started_at);

-- Table: retention_state
-- Stores retention policy configuration and current database size
-- Single-row table (id always equals 1)
CREATE TABLE retention_state (
  id INTEGER PRIMARY KEY CHECK (id=1),
  max_bytes INTEGER,
  max_age_days INTEGER,
  approx_db_bytes INTEGER NOT NULL,
  updated_at DATETIME NOT NULL
);

-- Initialize retention_state with default values
INSERT INTO retention_state(id, max_bytes, max_age_days, approx_db_bytes, updated_at)
VALUES (1, NULL, 30, 0, DATETIME('now'));
