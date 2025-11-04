---
id: task-022
title: Create domain models for sync operations and batches
status: To Do
assignee: []
created_date: '2025-11-04 18:35'
labels:
  - backend
  - domain
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement Java entity classes (SyncOperation, SyncBatch, RetentionState) with proper JPA/Hibernate annotations to map to the SQLite schema. Include enums for operation types and results.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 SyncOperation entity created with all fields from schema
- [ ] #2 SyncBatch entity created with all fields from schema
- [ ] #3 RetentionState entity created with all fields from schema
- [ ] #4 OpType enum created (SCRAM_UPSERT, SCRAM_DELETE, ACL_CREATE, ACL_DELETE)
- [ ] #5 OperationResult enum created (SUCCESS, ERROR, SKIPPED)
- [ ] #6 ScramMechanism enum created (SCRAM_SHA_256, SCRAM_SHA_512)
- [ ] #7 All entities include proper JPA annotations (@Entity, @Table, @Id, @Column)
- [ ] #8 Unit tests validate entity creation and field mappings
<!-- AC:END -->
