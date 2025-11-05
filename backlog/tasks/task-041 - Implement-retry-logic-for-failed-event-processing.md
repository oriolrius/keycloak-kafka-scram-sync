---
id: task-041
title: Implement retry logic for failed event processing
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-05 10:16'
updated_date: '2025-11-05 10:36'
labels:
  - sprint-4
  - retry
  - robustness
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add exponential backoff retry mechanism for events that fail to process (e.g., due to transient Kafka/Keycloak errors). Failed events should be retried up to N times before being marked as permanently failed and logged.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Retry logic supports configurable max attempts (default 3)
- [ ] #2 Exponential backoff delay between retries (e.g., 1s, 2s, 4s)
- [ ] #3 Failed events after max retries are logged with error details
- [ ] #4 Retry counter included in sync_operation table
- [ ] #5 Prometheus metric tracks retry attempts (sync_retry_total counter)
- [ ] #6 Unit tests validate retry behavior and backoff timing
<!-- AC:END -->
