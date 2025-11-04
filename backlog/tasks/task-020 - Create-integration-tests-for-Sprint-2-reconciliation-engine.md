---
id: task-020
title: Create integration tests for Sprint 2 reconciliation engine
status: To Do
assignee: []
created_date: '2025-11-04 18:33'
labels:
  - backend
  - testing
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement comprehensive integration tests that validate the complete reconciliation flow with real Testcontainers for Kafka and Keycloak.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 ReconciliationIntegrationTest created using @QuarkusTest
- [ ] #2 Uses existing IntegrationTestResource for Kafka and Keycloak containers
- [ ] #3 Test creates users in Keycloak via Admin API
- [ ] #4 Test triggers reconciliation and validates SCRAM credentials created in Kafka
- [ ] #5 Test validates sync_operation records persisted to SQLite
- [ ] #6 Test validates sync_batch records with correct counts
- [ ] #7 Test validates metrics incremented correctly
- [ ] #8 Test scenario: new users (upsert operations)
- [ ] #9 Test scenario: deleted users (delete operations)
- [ ] #10 Test scenario: no changes (empty diff)
- [ ] #11 Test validates error handling with invalid credentials
- [ ] #12 All integration tests pass with Testcontainers
<!-- AC:END -->
