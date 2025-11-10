---
id: task-071
title: Filter user sync by realm list configuration
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-10 17:01'
updated_date: '2025-11-10 17:38'
labels: []
dependencies: []
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Currently, the sync agent synchronizes all users from Keycloak to Kafka regardless of their realm. This can be inefficient and may sync unnecessary users in multi-realm deployments. 

This task implements realm-based filtering so that only users from specified realms are synchronized. The list of realms to sync should be configurable via SPI configuration, allowing operators to control which realms participate in the sync process.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 SPI accepts a configuration parameter for a comma-separated list of realm names to sync
- [x] #2 When realm list is configured, only users from specified realms are synced to Kafka
- [x] #3 When realm list is empty or not configured, all users are synced (backward compatible)
- [x] #4 Users from non-listed realms are filtered out and do not trigger sync events
- [x] #5 Configuration is validated at startup and logs clear messages about which realms will be synced
- [x] #6 Existing unit tests pass and new tests verify realm filtering logic
- [x] #7 Documentation updated with configuration parameter name, format, and examples
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add a static field in PasswordSyncEventListenerFactory to store the list of allowed realms
2. In the Factory's init() method, read the realm list from Config.Scope (support both comma-separated and Java system property)
3. Parse and validate the realm list, logging which realms will be synced
4. Pass the realm list to PasswordSyncEventListener constructor
5. In handlePasswordEvent(), extract the realm name from the AdminEvent
6. Before calling syncPasswordToKafka(), check if realm filtering is enabled and if the realm is in the allowed list
7. Skip sync and log an info message if realm is not in the list
8. Write unit tests for realm filtering logic
9. Run existing tests to ensure no regressions
10. Update README.md with new configuration parameter and examples
<!-- SECTION:PLAN:END -->
