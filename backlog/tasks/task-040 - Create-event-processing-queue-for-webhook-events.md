---
id: task-040
title: Create event processing queue for webhook events
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-05 10:16'
updated_date: '2025-11-05 10:31'
labels:
  - sprint-4
  - queue
  - backend
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement an in-memory queue (or persistent queue) to decouple webhook ingestion from event processing. Events received via webhook should be enqueued and processed asynchronously to prevent blocking the HTTP endpoint and enable retry logic.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Event queue accepts webhook events with correlation ID
- [ ] #2 Queue supports configurable capacity limit
- [ ] #3 Queue overflow behavior is defined (reject or oldest-drop)
- [ ] #4 Events are processed asynchronously by worker thread(s)
- [ ] #5 Queue status metric exposed (sync_queue_backlog gauge)
- [ ] #6 Integration test validates queue behavior under load
<!-- AC:END -->
