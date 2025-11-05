---
id: task-044
title: Add Prometheus metrics for webhook and retry operations
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-05 10:16'
updated_date: '2025-11-05 16:31'
labels:
  - sprint-4
  - metrics
  - observability
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Extend the Prometheus metrics endpoint to include counters and histograms for webhook event ingestion, queue depth, retry attempts, and processing latency. These metrics enable monitoring of the event-driven synchronization pipeline.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Counter sync_webhook_received_total{realm,event_type,result} tracks incoming events
- [ ] #2 Gauge sync_queue_backlog shows current queue depth
- [ ] #3 Counter sync_retry_total{reason,attempt} tracks retry operations
- [ ] #4 Histogram sync_webhook_processing_duration_seconds measures event processing time
- [ ] #5 Counter sync_webhook_signature_failures_total tracks authentication failures
- [ ] #6 All metrics exposed via GET /metrics in Prometheus format
- [ ] #7 Integration test validates metric presence and accuracy
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Analyze existing metrics implementation to understand patterns and conventions
2. Add new metric methods to SyncMetrics.java:
   - incrementWebhookReceived(realm, eventType, result)
   - incrementSignatureFailure()
   - startWebhookProcessingTimer()
   - recordWebhookProcessingDuration(sample, realm, eventType)
   - Update incrementRetryAttempts to include reason and attempt tags
3. Instrument KeycloakWebhookResource to track:
   - Webhook received events with result (success/error)
   - Processing duration
4. Instrument WebhookSignatureValidator to track signature failures
5. Instrument EventProcessor to track:
   - Processing duration for each event
   - Update retry metrics to include reason and attempt number
6. Verify sync_queue_backlog gauge is properly registered (already done in EventQueueService)
7. Write integration test to validate all metrics are exposed
8. Run tests and verify all pass
<!-- SECTION:PLAN:END -->
