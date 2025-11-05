---
id: task-036
title: Add Prometheus metrics for retention operations
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-05 06:17'
updated_date: '2025-11-05 09:54'
labels:
  - sprint-3
  - retention
  - metrics
  - observability
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Expose retention-related Prometheus metrics to enable monitoring of purge operations, database size, and retention policy enforcement. Metrics should align with the specification in the decision document.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Counter sync_purge_runs_total{reason} tracks purge executions (scheduled, post-batch)
- [ ] #2 Gauge sync_db_size_bytes reports current database size in bytes
- [ ] #3 Gauge sync_retention_max_bytes reports configured max_bytes limit
- [ ] #4 Gauge sync_retention_max_age_days reports configured max_age_days
- [ ] #5 Timer sync_purge_duration_seconds measures purge operation duration
- [ ] #6 Metrics are exposed at /metrics endpoint in Prometheus format
- [ ] #7 Unit tests verify metric registration and updates
<!-- AC:END -->
