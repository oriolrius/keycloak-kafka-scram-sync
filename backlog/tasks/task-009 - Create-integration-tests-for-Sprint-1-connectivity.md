---
id: task-009
title: Create integration tests for Sprint 1 connectivity
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-04 14:34'
updated_date: '2025-11-04 17:52'
labels:
  - backend
  - testing
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement integration tests that validate connectivity to Kafka, Keycloak, and SQLite. Use Testcontainers or similar to spin up real dependencies and verify all Sprint 1 components work together.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Integration test validates Kafka AdminClient connection
- [ ] #2 Integration test validates Keycloak Admin client authentication
- [ ] #3 Integration test validates SQLite database operations
- [ ] #4 Integration test validates /healthz endpoint returns correct status
- [ ] #5 Integration test validates /readyz endpoint returns correct status
- [ ] #6 Integration test validates /metrics endpoint returns Prometheus format
- [ ] #7 Tests use Testcontainers or equivalent for real dependencies
- [ ] #8 All tests pass in CI/CD pipeline
<!-- AC:END -->
