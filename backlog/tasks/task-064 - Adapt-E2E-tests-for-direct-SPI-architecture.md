---
id: task-064
title: Adapt E2E tests for direct SPI architecture
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-09 11:18'
updated_date: '2025-11-09 12:23'
labels:
  - testing
  - e2e
dependencies:
  - task-063
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Modify existing E2E tests in tests/api/scram-authentication-e2e.spec.ts to work with direct Kafka SPI. Remove webhook-related test steps, adjust test expectations for synchronous behavior. Tests should verify: user creation in Keycloak triggers immediate Kafka sync, password changes sync immediately, failed Kafka connection prevents password change.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 E2E test no longer waits for webhook cache or reconciliation
- [ ] #2 Test validates immediate SCRAM credential creation on password set
- [ ] #3 Test verifies SCRAM authentication works immediately after user creation
- [ ] #4 Test confirms Kafka downtime prevents password changes
- [ ] #5 All E2E tests pass with direct SPI architecture
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Read existing E2E test file (tests/api/scram-authentication-e2e.spec.ts)
2. Identify and remove webhook-related test steps and waits
3. Remove waits for webhook cache or reconciliation delays
4. Update test expectations for synchronous behavior (immediate sync)
5. Verify test validates immediate SCRAM credential creation on password set
6. Verify test checks SCRAM authentication works immediately after user creation
7. Add/update test for Kafka downtime preventing password changes
8. Run E2E tests to ensure they pass with direct SPI architecture
9. Document changes in implementation notes
<!-- SECTION:PLAN:END -->
