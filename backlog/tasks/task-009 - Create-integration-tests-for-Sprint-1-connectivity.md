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

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add Testcontainers dependencies (Kafka, Keycloak) to pom.xml
2. Create test profile configuration for integration tests
3. Create IntegrationTestResource with Testcontainers lifecycle management
4. Implement ConnectivityIntegrationTest:
   - Test Kafka AdminClient connection (AC#1)
   - Test Keycloak Admin client authentication (AC#2)
   - Test SQLite database operations (AC#3)
5. Implement HealthEndpointsIntegrationTest:
   - Test /q/health/ready endpoint (readiness with all checks) (AC#4, AC#5)
   - Test /q/health/live endpoint (liveness)
   - Test /q/metrics endpoint (Prometheus format) (AC#6)
6. Run all tests and verify they pass (AC#7, AC#8)
7. Document any CI/CD requirements
<!-- SECTION:PLAN:END -->
