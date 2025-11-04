---
id: task-017
title: Implement core reconciliation service
status: To Do
assignee: []
created_date: '2025-11-04 18:33'
labels:
  - backend
  - sync
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Create the main ReconciliationService that orchestrates the complete sync cycle: fetch from Keycloak, fetch from Kafka, compute diff, execute changes, and persist results.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 ReconciliationService created with performReconciliation method
- [ ] #2 Orchestrates full sync flow: Keycloak fetch → Kafka fetch → diff → execute → persist
- [ ] #3 Generates unique correlation_id for each reconciliation run
- [ ] #4 Creates sync_batch record at start with source (SCHEDULED/MANUAL/WEBHOOK)
- [ ] #5 For each upsert: generates SCRAM credentials using password (initially random)
- [ ] #6 Executes Kafka AdminClient alterUserScramCredentials in batches
- [ ] #7 Records each operation result (success/error) in sync_operation table
- [ ] #8 Updates sync_batch with final counts (items_total, items_success, items_error)
- [ ] #9 Implements error handling with partial failure support (continue on individual errors)
- [ ] #10 Returns ReconciliationResult summary object
- [ ] #11 Logs reconciliation start, progress, and completion with timings
- [ ] #12 Unit tests with mocked dependencies validate orchestration logic
- [ ] #13 Integration test validates end-to-end reconciliation flow
<!-- AC:END -->
