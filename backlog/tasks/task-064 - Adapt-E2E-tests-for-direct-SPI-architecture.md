---
id: task-064
title: Adapt E2E tests for direct SPI architecture
status: To Do
assignee: []
created_date: '2025-11-09 11:18'
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
