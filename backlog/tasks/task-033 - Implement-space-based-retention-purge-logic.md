---
id: task-033
title: Implement space-based retention purge logic
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-05 06:17'
updated_date: '2025-11-05 06:26'
labels:
  - sprint-3
  - retention
  - backend
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement size-based retention that purges oldest sync_operation records when database size exceeds max_bytes threshold. Uses SQLite PRAGMA page_count and page_size to calculate current database size, then deletes oldest entries until under the limit.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Service calculates db_bytes using PRAGMA page_count * page_size
- [ ] #2 Purge deletes oldest records (by occurred_at) when db_bytes > max_bytes
- [ ] #3 Purge operation updates retention_state.approx_db_bytes after deletion
- [ ] #4 Optional VACUUM is executed to reclaim disk space
- [ ] #5 Unit tests verify size calculation and deletion logic
- [ ] #6 Handles case where max_bytes is NULL (unlimited)
<!-- AC:END -->
