---
id: task-011
title: Create domain models for sync operations and batches
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-04 18:33'
updated_date: '2025-11-04 18:51'
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

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Create enums package (com.miimetiq.keycloak.sync.domain.enums) with OpType, OperationResult, and ScramMechanism enums
2. Create entity package (com.miimetiq.keycloak.sync.domain.entity) with SyncOperation, SyncBatch, and RetentionState entities
3. Add proper JPA/Hibernate annotations to all entities (@Entity, @Table, @Id, @GeneratedValue, @Column, @Enumerated)
4. Create unit tests in test package to validate entity creation and field mappings
5. Verify all acceptance criteria are met
<!-- SECTION:PLAN:END -->
