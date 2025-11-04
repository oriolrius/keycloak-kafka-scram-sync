---
id: task-015
title: Implement synchronization diff engine
status: Done
assignee:
  - '@claude'
created_date: '2025-11-04 18:33'
updated_date: '2025-11-04 21:07'
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

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implemented complete synchronization diff engine with the following components:

**Core Implementation:**
- Created SyncPlan DTO (reconcile/SyncPlan.java) containing upserts, deletes, statistics, and builder pattern
- Implemented SyncDiffEngine service (reconcile/SyncDiffEngine.java) with computeDiff method that compares Keycloak users against Kafka principals
- Added configurable exclusion patterns for system accounts (admin, kafka, zookeeper, system)
- Implemented "always upsert" mode (configurable via reconcile.always-upsert property) to refresh all credentials vs only new users

**Diff Logic:**
- Upserts: Users in Keycloak but not in Kafka, or all users if alwaysUpsert=true
- Deletes: Principals in Kafka but not in Keycloak (orphaned accounts)
- Filtering: Excludes system accounts based on exact and prefix matching
- Performance optimized with HashSet lookups for O(1) comparisons

**Testing:**
- Comprehensive unit tests (SyncDiffEngineTest) covering:
  - New users only scenario
  - Deleted users only scenario  
  - No changes scenario
  - Mixed operations scenario
  - Exclusion filtering
  - Dry-run mode
  - Empty inputs
  - Large dataset (10,000+ users) performance test
  - Immutability of returned lists
  - Builder pattern functionality

**Configuration Properties:**
- reconcile.excluded-principals: Comma-separated list of principals to exclude
- reconcile.always-upsert: Boolean to refresh all credentials (default: true)

All acceptance criteria met and tested successfully.
<!-- SECTION:NOTES:END -->
