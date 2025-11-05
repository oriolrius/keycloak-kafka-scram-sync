---
id: task-043
title: Add circuit breaker for Keycloak and Kafka connectivity
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-05 10:16'
updated_date: '2025-11-05 10:54'
labels:
  - sprint-4
  - robustness
  - resilience
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement circuit breaker pattern to handle repeated failures when connecting to Keycloak or Kafka. When a service is unreachable, the circuit breaker should open and prevent further connection attempts for a configured period, improving resilience and reducing resource waste.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Circuit breaker opens after N consecutive failures (default 5)
- [ ] #2 Circuit remains open for configurable timeout (default 60s)
- [ ] #3 Circuit automatically attempts half-open state after timeout
- [ ] #4 Successful operations in half-open state close the circuit
- [ ] #5 Failed operations in half-open state reopen the circuit
- [ ] #6 Circuit breaker state exposed via health endpoint details
- [ ] #7 Unit tests validate state transitions
<!-- AC:END -->
