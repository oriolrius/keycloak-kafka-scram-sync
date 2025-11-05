---
id: task-036
title: Add Prometheus metrics for retention operations
status: Done
assignee:
  - '@claude'
created_date: '2025-11-05 06:17'
updated_date: '2025-11-05 10:04'
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
- [x] #1 Counter sync_purge_runs_total{reason} tracks purge executions (scheduled, post-batch)
- [x] #2 Gauge sync_db_size_bytes reports current database size in bytes
- [x] #3 Gauge sync_retention_max_bytes reports configured max_bytes limit
- [x] #4 Gauge sync_retention_max_age_days reports configured max_age_days
- [x] #5 Timer sync_purge_duration_seconds measures purge operation duration
- [x] #6 Metrics are exposed at /metrics endpoint in Prometheus format
- [x] #7 Unit tests verify metric registration and updates
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Review existing SyncMetrics.java to understand current metric patterns
2. Add new metrics to SyncMetrics class:
   - Counter sync_purge_runs_total with reason tag
   - Gauges for retention config (max_bytes, max_age_days)
   - Timer sync_purge_duration_seconds
   - Note: sync_db_size_bytes already exists
3. Instrument RetentionService.java to record purge metrics
4. Instrument RetentionScheduler.java to record scheduled vs manual purge reasons
5. Update MetricsInitializer if needed for gauge initialization
6. Write unit tests in RetentionServiceTest or new MetricsTest
7. Verify metrics are exposed at /metrics endpoint
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Summary

Added comprehensive Prometheus metrics for retention operations to enable monitoring of purge jobs, database size, and retention policy enforcement.

## Implementation Details

### Metrics Added

**Counters:**
- `sync_purge_runs_total{reason}` - Tracks purge executions with tags `scheduled` and `post-batch`

**Gauges:**
- `sync_db_size_bytes` - Reports current database size (already existed, now updated during purges)
- `sync_retention_max_bytes` - Reports configured max_bytes limit
- `sync_retention_max_age_days` - Reports configured max_age_days limit

**Timers:**
- `sync_purge_duration_seconds` - Measures total duration of purge operations (TTL + size + VACUUM)

### Code Changes

1. **SyncMetrics.java** (src/main/java/com/miimetiq/keycloak/sync/metrics/SyncMetrics.java:33-35,48-50,198-244)
   - Added AtomicLong fields for retention config gauges
   - Registered new gauges in init() method  
   - Added incrementPurgeRuns(), startPurgeTimer(), recordPurgeDuration(), updateRetentionConfig() methods

2. **RetentionService.java** (src/main/java/com/miimetiq/keycloak/sync/service/RetentionService.java:4,7,45,90,124,231)
   - Injected SyncMetrics
   - Updated purgeTtl() to call syncMetrics.updateDatabaseSize()
   - Updated purgeBySize() to call syncMetrics.updateDatabaseSize()
   - Updated updateRetentionConfig() to call syncMetrics.updateRetentionConfig()

3. **RetentionScheduler.java** (src/main/java/com/miimetiq/keycloak/sync/retention/RetentionScheduler.java:3,5,35,66,89-94,138,158)
   - Injected SyncMetrics
   - Modified executePurge() to accept reason parameter
   - Added timer instrumentation around purge execution
   - Added counter increments for each purge run
   - Updated callers to pass "scheduled" or "post-batch" reasons

4. **MetricsInitializer.java** (src/main/java/com/miimetiq/keycloak/sync/metrics/MetricsInitializer.java:4,5,23,29-36)
   - Injected RetentionRepository
   - Added startup logic to load initial retention config values and update metrics

### Testing

1. **SyncMetricsTest.java** - New test file with 10 tests verifying:
   - Gauge registration for retention config metrics
   - updateRetentionConfig() updates gauge values correctly
   - Null config values set gauges to 0
   - Counter increments for different purge reasons
   - Timer records purge durations
   - Database size updates from file
   - Handles missing files gracefully

2. **RetentionServiceTest.java** - Added 2 integration tests:
   - Verifies updateRetentionConfig() updates metrics
   - Verifies null config values set metrics to 0

3. **RetentionSchedulerTest.java** - Updated all tests to pass reason parameter

### Test Results

All tests passing:
- SyncMetricsTest: 10/10 ✓
- RetentionServiceTest: 19/19 ✓  
- RetentionSchedulerTest: 11/11 ✓

## Configuration

No configuration changes required. Metrics are automatically exposed at `/metrics` endpoint in Prometheus format via existing Quarkus Micrometer integration.

## Verification

Metrics can be verified by:
1. Starting the application
2. Accessing http://localhost:57010/metrics
3. Looking for metrics with prefix `sync_purge_`, `sync_retention_`, and `sync_db_size_bytes`
<!-- SECTION:NOTES:END -->
