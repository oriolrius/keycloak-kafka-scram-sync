---
id: task-033
title: Implement space-based retention purge logic
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-05 06:17'
updated_date: '2025-11-05 06:26'
labels:
  - sprint-3
  - retention
  - backend
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement size-based retention that purges oldest sync_operation records when database size exceeds max_bytes threshold. Uses SQLite PRAGMA page_count and page_size to calculate current database size, then deletes oldest entries until under the limit.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Service calculates db_bytes using PRAGMA page_count * page_size
- [ ] #2 Purge deletes oldest records (by occurred_at) when db_bytes > max_bytes
- [ ] #3 Purge operation updates retention_state.approx_db_bytes after deletion
- [ ] #4 Optional VACUUM is executed to reclaim disk space
- [ ] #5 Unit tests verify size calculation and deletion logic
- [ ] #6 Handles case where max_bytes is NULL (unlimited)
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add database size calculation method to RetentionService
   - Use EntityManager to execute PRAGMA page_count and PRAGMA page_size
   - Calculate db_bytes = page_count * page_size
   - Return current database size in bytes

2. Implement purgeBySize() method in RetentionService
   - Read max_bytes from retention_state
   - Return 0 if max_bytes is null (unlimited)
   - Calculate current db_bytes using PRAGMA
   - If db_bytes <= max_bytes, no purge needed
   - While db_bytes > max_bytes:
     - Delete oldest records in batches (by occurred_at ASC)
     - Recalculate db_bytes
   - Update retention_state.approx_db_bytes with final size
   - Update retention_state.updated_at

3. Add optional VACUUM support
   - Create executeVacuum() method
   - Execute VACUUM to reclaim disk space after deletions
   - Make it optional (can be called after purge if needed)

4. Write comprehensive unit tests
   - Test calculateDatabaseSize() returns valid size
   - Test purgeBySize() with db over limit
   - Test purgeBySize() with db under limit
   - Test purgeBySize() with null max_bytes (unlimited)
   - Test that approx_db_bytes is updated correctly
   - Test VACUUM execution

5. Ensure all operations are transactional
<!-- SECTION:PLAN:END -->
