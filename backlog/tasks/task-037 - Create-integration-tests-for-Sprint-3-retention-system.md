---
id: task-037
title: Create integration tests for Sprint 3 retention system
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-05 06:17'
updated_date: '2025-11-05 10:05'
labels:
  - sprint-3
  - retention
  - testing
  - integration
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Develop comprehensive integration tests that verify the complete retention system behavior including TTL purge, space purge, scheduled execution, API endpoints, and metrics exposure. Tests should use real SQLite database and validate end-to-end flows.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Test verifies TTL purge deletes expired records correctly
- [ ] #2 Test verifies space-based purge when database exceeds max_bytes
- [ ] #3 Test verifies GET /api/config/retention returns accurate state
- [ ] #4 Test verifies PUT /api/config/retention updates configuration
- [ ] #5 Test verifies scheduled purge job executes at configured intervals
- [ ] #6 Test verifies retention metrics are correctly exposed
- [ ] #7 Test verifies post-batch purge triggers work correctly
- [ ] #8 All tests use real SQLite with Flyway migrations
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Analyze existing retention test coverage (RetentionConfigResourceIntegrationTest covers AC #3 and #4 for API endpoints)
2. Create new RetentionIntegrationTest.java for system-level retention behavior tests
3. Implement test setup with test data seeding methods (create old and recent sync operations)
4. Implement AC #1: Test TTL-based purge deletes expired records correctly
5. Implement AC #2: Test space-based purge when database exceeds max_bytes
6. Implement AC #5: Test scheduled purge job execution (use RetentionScheduler.executePurge directly)
7. Implement AC #6: Test retention metrics exposure via Prometheus endpoint
8. Implement AC #7: Test post-batch purge trigger works correctly
9. Verify AC #8: Confirm all tests use real SQLite with Flyway migrations (via @QuarkusTest)
10. Run all tests and verify they pass
11. Add implementation notes for PR description
<!-- SECTION:PLAN:END -->
