---
id: task-035
title: Implement scheduled retention purge job
status: To Do
assignee: []
created_date: '2025-11-05 06:17'
labels:
  - sprint-3
  - retention
  - backend
  - scheduling
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Create a scheduled background job that executes retention purge logic periodically (configurable via RETENTION_PURGE_INTERVAL_SECONDS). The job should also trigger after each sync batch completes to maintain database size limits.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Scheduled job runs every RETENTION_PURGE_INTERVAL_SECONDS (default 300s)
- [ ] #2 Job executes both TTL and space-based purge logic
- [ ] #3 Purge is triggered after each sync_batch completion
- [ ] #4 Job handles failures gracefully and logs errors
- [ ] #5 Configuration loads RETENTION_PURGE_INTERVAL_SECONDS from environment
- [ ] #6 Unit tests verify scheduling behavior and error handling
<!-- AC:END -->
