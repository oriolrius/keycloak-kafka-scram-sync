---
id: task-042
title: Parse and map Keycloak admin event types to sync operations
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-05 10:16'
updated_date: '2025-11-05 10:40'
labels:
  - sprint-4
  - webhook
  - backend
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement parsing logic to extract relevant information from Keycloak Admin Event payloads and map them to internal sync operations (UPSERT/DELETE). Support event types: user create, user update, user delete, password change, client create/update/delete.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Parser extracts realm, principal, and event type from payload
- [ ] #2 User create/update events map to UPSERT operations
- [ ] #3 User delete events map to DELETE operations
- [ ] #4 Password change events trigger SCRAM credential regeneration
- [ ] #5 Client create/update/delete events are supported
- [ ] #6 Unknown event types are logged and ignored gracefully
- [ ] #7 Unit tests cover all supported event types
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Create EventType enum to map Keycloak event types
2. Create EventMapper service to parse and map events to sync operations
3. Wire EventMapper into EventProcessor to replace placeholder processing
4. Support user operations (CREATE, UPDATE, DELETE, PASSWORD)
5. Support client operations (CREATE, UPDATE, DELETE)
6. Handle unknown event types gracefully
7. Write unit tests for event mapping logic
8. Update all acceptance criteria
<!-- SECTION:PLAN:END -->
