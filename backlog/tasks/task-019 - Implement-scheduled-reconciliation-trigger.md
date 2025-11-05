---
id: task-019
title: Implement scheduled reconciliation trigger
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-04 18:33'
updated_date: '2025-11-05 04:24'
labels:
  - backend
  - scheduling
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Create a scheduled job that triggers periodic reconciliation based on configured interval. This ensures continuous synchronization between Keycloak and Kafka.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 ReconciliationScheduler created with @Scheduled annotation
- [ ] #2 Uses RECONCILE_INTERVAL_SECONDS from configuration (default 120s)
- [ ] #3 Triggers ReconciliationService.performReconciliation with source=SCHEDULED
- [ ] #4 Implements proper exception handling to prevent scheduler death on errors
- [ ] #5 Logs scheduled reconciliation trigger and result
- [ ] #6 Skips execution if previous reconciliation still running (no overlapping)
- [ ] #7 Exposes manual trigger endpoint POST /api/reconcile/trigger for testing
- [ ] #8 Configuration allows disabling scheduled reconciliation (enabled by default)
- [ ] #9 Unit test validates scheduler configuration
- [ ] #10 Integration test validates scheduled execution occurs
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Review ReconcileConfig to understand interval configuration
2. Create ReconciliationScheduler with @Scheduled annotation
3. Implement scheduled execution with overlap prevention
4. Add exception handling to prevent scheduler death
5. Create REST endpoint for manual trigger
6. Add configuration to enable/disable scheduling
7. Add tests for scheduler and REST endpoint
<!-- SECTION:PLAN:END -->
