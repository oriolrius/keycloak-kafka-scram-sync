---
id: task-037
title: Create integration tests for Sprint 3 retention system
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-05 06:17'
updated_date: '2025-11-05 10:04'
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
