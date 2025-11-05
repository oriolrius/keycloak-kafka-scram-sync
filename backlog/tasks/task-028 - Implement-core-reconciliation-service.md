---
id: task-028
title: Implement core reconciliation service
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-04 18:35'
updated_date: '2025-11-05 05:11'
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

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Analyze current ReconciliationService and identify gaps vs acceptance criteria
2. Update ReconciliationService to use diff-based reconciliation:
   - Fetch Kafka principals using KafkaScramManager.describeUserScramCredentials()
   - Integrate SyncDiffEngine to compute diff between Keycloak and Kafka
   - Process upserts from SyncPlan (existing logic)
   - Add delete operations for orphaned principals
   - Update batch tracking for both upserts and deletes
3. Enhance error handling for delete operations (similar to upserts)
4. Update metrics to track both upsert and delete operations
5. Create comprehensive unit tests (ReconciliationServiceTest):
   - Test happy path with mocked dependencies
   - Test diff computation integration
   - Test upsert-only scenario
   - Test delete-only scenario  
   - Test mixed upsert+delete scenario
   - Test error handling with partial failures
   - Test metrics recording
6. Verify integration tests still pass
7. Update task ACs and notes
<!-- SECTION:PLAN:END -->
