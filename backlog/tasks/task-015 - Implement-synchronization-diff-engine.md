---
id: task-015
title: Implement synchronization diff engine
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-04 18:33'
updated_date: '2025-11-04 20:57'
labels:
  - backend
  - sync
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Create the diff engine that compares Keycloak users with Kafka SCRAM principals and computes the required synchronization operations (upserts and deletes).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 SyncDiffEngine service created with computeDiff method
- [ ] #2 Takes KeycloakUserInfo list and Kafka principal set as input
- [ ] #3 Returns SyncPlan object containing lists of upserts and deletes
- [ ] #4 Upsert identified when user exists in Keycloak but not in Kafka
- [ ] #5 Upsert identified when user exists in both but credentials may be stale (configurable)
- [ ] #6 Delete identified when principal exists in Kafka but not in Keycloak
- [ ] #7 Filters out excluded principals (admin accounts, system accounts)
- [ ] #8 SyncPlan includes counts and summary statistics
- [ ] #9 Implements dry-run mode for validation without execution
- [ ] #10 Unit tests validate diff logic with various scenarios (new users, deletions, no changes)
- [ ] #11 Performance test validates handling of 10,000+ users
<!-- AC:END -->
