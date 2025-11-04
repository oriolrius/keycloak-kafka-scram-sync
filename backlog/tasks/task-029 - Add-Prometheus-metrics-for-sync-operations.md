---
id: task-029
title: Add Prometheus metrics for sync operations
status: To Do
assignee: []
created_date: '2025-11-04 18:35'
labels:
  - backend
  - observability
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement Micrometer-based metrics for the reconciliation engine. Track counters for operations, gauges for state, and timers for performance monitoring.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Counter sync_kc_fetch_total created with labels (realm, source)
- [ ] #2 Counter sync_kafka_scram_upserts_total created with labels (cluster_id, mechanism, result)
- [ ] #3 Counter sync_kafka_scram_deletes_total created with labels (cluster_id, result)
- [ ] #4 Gauge sync_db_size_bytes created (tracks SQLite database size)
- [ ] #5 Gauge sync_last_success_epoch_seconds created (tracks last successful reconciliation)
- [ ] #6 Timer sync_reconcile_duration_seconds created with labels (realm, cluster_id, source)
- [ ] #7 Timer sync_admin_op_duration_seconds created with label (op)
- [ ] #8 Metrics service created to centralize metric recording
- [ ] #9 All counters increment in appropriate service methods
- [ ] #10 All timers record in appropriate service methods
- [ ] #11 All gauges update based on system state
- [ ] #12 /q/metrics endpoint exposes all new metrics in Prometheus format
- [ ] #13 Integration test validates metrics are incremented during reconciliation
<!-- AC:END -->
