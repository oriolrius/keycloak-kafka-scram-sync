---
id: task-045
title: Create integration tests for Sprint 4 webhook and robustness
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-05 10:16'
updated_date: '2025-11-05 16:43'
labels:
  - sprint-4
  - testing
  - integration
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement comprehensive integration tests covering the webhook endpoint, signature verification, event queue, retry logic, and circuit breaker functionality. Tests should use Testcontainers for Keycloak and Kafka to validate end-to-end behavior.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Test validates webhook endpoint accepts valid signed events
- [ ] #2 Test confirms invalid signatures are rejected with 401
- [ ] #3 Test verifies events are enqueued and processed asynchronously
- [ ] #4 Test validates retry logic for transient failures
- [ ] #5 Test confirms circuit breaker opens after repeated failures
- [ ] #6 Test validates metrics are correctly updated
- [ ] #7 All integration tests pass in CI environment
<!-- AC:END -->
