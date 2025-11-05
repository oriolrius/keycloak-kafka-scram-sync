---
id: task-032
title: Implement TTL-based retention purge logic
status: Done
assignee:
  - '@claude'
created_date: '2025-11-05 06:17'
updated_date: '2025-11-05 06:24'
labels:
  - sprint-3
  - retention
  - backend
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement time-based retention that deletes sync_operation records older than the configured max_age_days threshold. This ensures the database doesn't grow indefinitely by removing historical data beyond the retention window.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Service reads max_age_days from retention_state table
- [x] #2 Purge logic deletes records where occurred_at < now() - max_age_days
- [x] #3 Purge operation is transactional and updates retention_state.updated_at
- [x] #4 Unit tests verify correct TTL calculation and deletion
- [x] #5 Purge handles edge cases (no records, all records expired)
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Create RetentionRepository extending PanacheRepository to access retention_state table
2. Implement RetentionService with purge logic that:
   - Reads max_age_days from retention_state (singleton row id=1)
   - Calculates cutoff date: now() - max_age_days
   - Deletes sync_operation records where occurred_at < cutoff
   - Updates retention_state.updated_at in same transaction
3. Write unit tests for RetentionRepository (find singleton)
4. Write unit tests for RetentionService purge logic:
   - Verify correct TTL calculation
   - Test deletion of old records
   - Test transactional behavior
   - Test edge cases: no records to purge, all records expired, max_age_days is null
5. Ensure all operations are transactional using @Transactional
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Summary

Implemented TTL-based retention purge logic for sync_operation records to prevent unbounded database growth. The system now automatically deletes operations older than the configured max_age_days threshold.

## Implementation Details

**Created Files:**
- `src/main/java/com/miimetiq/keycloak/sync/repository/RetentionRepository.java` - Repository for accessing retention_state singleton table
- `src/main/java/com/miimetiq/keycloak/sync/service/RetentionService.java` - Service with TTL purge logic and retention configuration management
- `src/test/java/com/miimetiq/keycloak/sync/repository/RetentionRepositoryTest.java` - Integration tests for RetentionRepository (6 tests)
- `src/test/java/com/miimetiq/keycloak/sync/service/RetentionServiceTest.java` - Integration tests for RetentionService (10 tests)

**Key Features:**
1. **RetentionRepository**: Provides access to the retention_state singleton with `findSingleton()` and `getOrThrow()` methods
2. **RetentionService.purgeTtl()**: 
   - Reads max_age_days from retention_state table
   - Calculates cutoff date (now - max_age_days)
   - Deletes sync_operation records where occurred_at < cutoff
   - Updates retention_state.updated_at timestamp
   - Returns count of deleted records
   - Handles edge cases: null max_age_days, no records to purge, all records expired
3. **Transaction Safety**: All operations use @Transactional to ensure atomicity
4. **Configuration Management**: Added updateRetentionConfig() method for dynamic configuration updates

**Test Coverage:**
- 16 total tests (all passing)
- Tests verify correct TTL calculation with different max_age_days values (7, 30 days)
- Edge case tests: no records to delete, all records expired, max_age_days is null
- Boundary condition tests ensuring records exactly at retention boundary are kept
- Transaction validation ensuring retention_state.updated_at is updated after purge

## Technical Decisions

- Used Panache repository pattern for consistency with existing codebase (SyncOperationRepository, SyncBatchRepository)
- Implemented retention_state as singleton access pattern with validation
- Purge uses simple query `delete("occurredAt < ?1", cutoffDate)` for efficiency
- All repository tests focus on read operations; update tests go through service layer to avoid transaction conflicts

## Testing

All tests pass successfully:
```
RetentionRepositoryTest: 6 tests, 0 failures
RetentionServiceTest: 10 tests, 0 failures
```

## Next Steps

This implementation provides the foundation for:
- task-33: Space-based retention purge logic
- task-35: Scheduled retention purge job (will call purgeTtl() periodically)
- task-36: Prometheus metrics for retention operations
<!-- SECTION:NOTES:END -->
