---
id: task-021
title: Implement SQLite schema for sync operations and retention
status: To Do
assignee: []
created_date: '2025-11-04 18:35'
labels:
  - backend
  - database
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Create Flyway migration V2 to add the sync_operation, sync_batch, and retention_state tables as defined in the technical analysis. These tables will store synchronization history and manage retention policies.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Flyway migration V2__sync_operations.sql created with all three tables
- [ ] #2 sync_operation table includes all required fields (id, correlation_id, occurred_at, realm, cluster_id, principal, op_type, mechanism, result, error_code, error_message, duration_ms)
- [ ] #3 sync_batch table includes all required fields (id, correlation_id, started_at, finished_at, source, items_total, items_success, items_error)
- [ ] #4 retention_state table includes all required fields with default values (max_age_days=30)
- [ ] #5 All appropriate indexes created (time, principal, type, correlation_id)
- [ ] #6 Migration runs successfully and integrates with existing V1 schema
- [ ] #7 Integration test validates all tables exist and are accessible
<!-- AC:END -->
