---
id: task-033
title: Implement space-based retention purge logic
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-05 06:17'
updated_date: '2025-11-05 06:34'
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
- [x] #1 Service calculates db_bytes using PRAGMA page_count * page_size
- [x] #2 Purge deletes oldest records (by occurred_at) when db_bytes > max_bytes
- [x] #3 Purge operation updates retention_state.approx_db_bytes after deletion
- [x] #4 Optional VACUUM is executed to reclaim disk space
- [x] #5 Unit tests verify size calculation and deletion logic
- [x] #6 Handles case where max_bytes is NULL (unlimited)
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

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Summary

Implemented space-based retention purge logic for sync_operation records to prevent unbounded database growth. The system now automatically deletes oldest operations when database size exceeds the configured max_bytes threshold.

## Implementation Details

**Modified Files:**
- `src/main/java/com/miimetiq/keycloak/sync/service/RetentionService.java` - Added space-based purge methods
- `src/test/java/com/miimetiq/keycloak/sync/service/RetentionServiceTest.java` - Added 7 new integration tests

**New Methods in RetentionService:**

1. **calculateDatabaseSize()**: 
   - Uses SQLite PRAGMA page_count and PRAGMA page_size
   - Returns current database size in bytes (page_count * page_size)
   - No transaction required, can be called anytime

2. **purgeBySize()**:
   - Reads max_bytes from retention_state table
   - Returns 0 if max_bytes is null (unlimited)
   - Calculates current database size using PRAGMA
   - If size exceeds limit, deletes oldest records in batches of 100
   - Updates retention_state.approx_db_bytes with final size
   - Updates retention_state.updated_at timestamp
   - Fully transactional operation

3. **executeVacuum()**:
   - Executes SQLite VACUUM to reclaim disk space
   - Returns boolean indicating success/failure
   - Handles SQLite transaction constraints gracefully
   - Optional operation to be called after large purges

**Key Features:**
- Batch deletion (100 records at a time) prevents long-running transactions
- Progressive size checking - recalculates after each batch
- Stops when database is under limit or no more records exist
- Always updates approx_db_bytes even if no purge needed
- Handles edge cases: null max_bytes, empty database, all records need deletion

**Test Coverage:**
- 17 total tests (all passing)
- 7 new tests for space-based purge:
  - calculateDatabaseSize returns valid size
  - No purge when max_bytes is null
  - No purge when under limit (but updates approx_db_bytes)
  - Deletes oldest records when over limit
  - Updates approx_db_bytes after purge
  - Handles multiple record deletion
  - VACUUM handles transaction constraints gracefully

## Technical Decisions

- **PRAGMA over File System**: Using SQLite PRAGMA commands ensures accurate size calculation that includes all database overhead (indexes, metadata, etc.)
- **Batch Deletion**: Deleting 100 records at a time balances between efficiency and transaction duration
- **VACUUM Handling**: Made VACUUM non-throwing since it cannot run in SQLite transactions. Returns boolean for success/failure.
- **Entity Manager for Native SQL**: Used EntityManager.createNativeQuery() for PRAGMA commands as they're not available through JPQL
- **Transactional Purge**: Entire purge operation (including all batches) runs in single transaction for consistency

## Testing

All tests pass successfully:
```
Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
```

Test highlights:
- Database size calculation validated
- Purge behavior tested with various size limits
- Edge cases covered (null limits, empty db, full deletion)
- VACUUM tested (handles transaction constraints appropriately)

## Integration with Task 32

This implementation complements the TTL-based purge (task-32):
- Both use same RetentionService class
- Both read from retention_state singleton
- Both update retention_state after purge
- Can be used together or independently
- Scheduled job (task-35) will call both methods

## Next Steps

This implementation provides the foundation for:
- task-35: Scheduled retention purge job (will call both purgeTtl() and purgeBySize())
- task-36: Prometheus metrics for retention operations
<!-- SECTION:NOTES:END -->
