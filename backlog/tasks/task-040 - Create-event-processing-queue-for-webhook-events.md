---
id: task-040
title: Create event processing queue for webhook events
status: Done
assignee:
  - '@claude'
created_date: '2025-11-05 10:16'
updated_date: '2025-11-05 10:36'
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
- [x] #1 Event queue accepts webhook events with correlation ID
- [x] #2 Queue supports configurable capacity limit
- [x] #3 Queue overflow behavior is defined (reject or oldest-drop)
- [x] #4 Events are processed asynchronously by worker thread(s)
- [x] #5 Queue status metric exposed (sync_queue_backlog gauge)
- [x] #6 Integration test validates queue behavior under load
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Create WebhookEvent wrapper class to hold event + correlation ID
2. Create EventQueueService with configurable capacity and overflow behavior
3. Add queue configuration properties (capacity, overflow-strategy)
4. Create async EventProcessor worker that polls and processes events
5. Add sync_queue_backlog gauge metric to SyncMetrics
6. Wire EventQueueService into KeycloakWebhookResource
7. Write integration test for queue behavior under load
8. Update all acceptance criteria
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Implementation Summary

Successfully implemented an event processing queue system for webhook events with the following components:

### Components Created

1. **WebhookEvent.java** - Wrapper class that encapsulates Keycloak admin events with tracking metadata (correlation ID, timestamps, retry counter)

2. **EventQueueService.java** - Application-scoped service managing a bounded in-memory queue with:
   - Configurable capacity (default: 1000 events)
   - Two overflow strategies: REJECT (default) and DROP_OLDEST
   - Thread-safe operations using BlockingQueue
   - Metrics exposure via Micrometer gauges

3. **EventProcessor.java** - Asynchronous processor with configurable worker threads (default: 2) that:
   - Starts on application startup
   - Continuously polls and processes events
   - Supports graceful shutdown
   - Logs processing activity with correlation IDs

4. **Configuration Properties** - Added to application.properties:
   - webhook.queue.capacity=1000
   - webhook.queue.overflow-strategy=REJECT
   - webhook.queue.worker-threads=2

5. **Metrics** - Exposed Prometheus metrics:
   - sync_queue_backlog - Current number of events in queue
   - sync_queue_dropped_total - Total events dropped due to overflow

### Integration

- Integrated EventQueueService into KeycloakWebhookResource
- Events are now enqueued asynchronously after validation
- Returns 503 SERVICE_UNAVAILABLE when queue is full (REJECT strategy)

### Testing

Created comprehensive integration test (EventQueueIntegrationTest.java) with 9 test cases covering:
- Event enqueueing with correlation ID
- Capacity limit enforcement
- Overflow behavior (REJECT strategy)
- Concurrent enqueueing
- REST endpoint integration
- Event polling
- Queue metrics
- Metadata preservation

All tests pass successfully.
<!-- SECTION:NOTES:END -->
