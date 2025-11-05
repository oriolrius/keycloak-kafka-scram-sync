---
id: task-035
title: Implement scheduled retention purge job
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-05 06:17'
updated_date: '2025-11-05 09:44'
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
- [x] #1 Scheduled job runs every RETENTION_PURGE_INTERVAL_SECONDS (default 300s)
- [x] #2 Job executes both TTL and space-based purge logic
- [x] #3 Purge is triggered after each sync_batch completion
- [x] #4 Job handles failures gracefully and logs errors
- [x] #5 Configuration loads RETENTION_PURGE_INTERVAL_SECONDS from environment
- [x] #6 Unit tests verify scheduling behavior and error handling
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Create RetentionScheduler.java following ReconciliationScheduler pattern
   - Use @Scheduled(every = "${retention.purge-interval-seconds}s")
   - Use SKIP concurrent execution policy
   - Add AtomicBoolean to prevent overlapping runs
   
2. Implement scheduled purge job
   - Call retentionService.purgeTtl() for time-based cleanup
   - Call retentionService.purgeBySize() for space-based cleanup
   - Call retentionService.executeVacuum() to reclaim space
   - Handle null limits gracefully (skip if not configured)
   
3. Add post-sync-batch purge trigger
   - Modify ReconciliationService.java after line 273 (batch completion)
   - Inject RetentionService and call purge methods
   - Run asynchronously to not block sync completion
   
4. Error handling and logging
   - Wrap purge calls in try-catch
   - Log errors but don't fail the job
   - Log purge results (operations deleted, space reclaimed)
   
5. Create RetentionSchedulerTest
   - Test scheduling behavior (interval configuration)
   - Test concurrent execution prevention
   - Test error handling (exceptions don't break scheduler)
   - Test that purge is called after sync batch
<!-- SECTION:PLAN:END -->
