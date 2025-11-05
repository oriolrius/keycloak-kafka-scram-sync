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
