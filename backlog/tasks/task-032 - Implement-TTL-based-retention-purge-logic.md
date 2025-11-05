---
id: task-032
title: Implement TTL-based retention purge logic
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-05 06:17'
updated_date: '2025-11-05 06:18'
labels:
  - sprint-3
  - retention
  - backend
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement time-based retention that deletes sync_operation records older than the configured max_age_days threshold. This ensures the database doesn't grow indefinitely by removing historical data beyond the retention window.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Service reads max_age_days from retention_state table
- [ ] #2 Purge logic deletes records where occurred_at < now() - max_age_days
- [ ] #3 Purge operation is transactional and updates retention_state.updated_at
- [ ] #4 Unit tests verify correct TTL calculation and deletion
- [ ] #5 Purge handles edge cases (no records, all records expired)
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Create RetentionRepository extending PanacheRepository to access retention_state table
2. Implement RetentionService with purge logic that:
   - Reads max_age_days from retention_state (singleton row id=1)
   - Calculates cutoff date: now() - max_age_days
   - Deletes sync_operation records where occurred_at < cutoff
   - Updates retention_state.updated_at in same transaction
3. Write unit tests for RetentionRepository (find singleton)
4. Write unit tests for RetentionService purge logic:
   - Verify correct TTL calculation
   - Test deletion of old records
   - Test transactional behavior
   - Test edge cases: no records to purge, all records expired, max_age_days is null
5. Ensure all operations are transactional using @Transactional
<!-- SECTION:PLAN:END -->
