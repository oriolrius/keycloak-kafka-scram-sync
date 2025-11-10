---
id: task-071
title: Filter user sync by realm list configuration
status: To Do
assignee: []
created_date: '2025-11-10 17:01'
updated_date: '2025-11-10 17:33'
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
- [ ] #1 SPI accepts a configuration parameter for a comma-separated list of realm names to sync
- [ ] #2 When realm list is configured, only users from specified realms are synced to Kafka
- [ ] #3 When realm list is empty or not configured, all users are synced (backward compatible)
- [ ] #4 Users from non-listed realms are filtered out and do not trigger sync events
- [ ] #5 Configuration is validated at startup and logs clear messages about which realms will be synced
- [ ] #6 Existing unit tests pass and new tests verify realm filtering logic
- [ ] #7 Documentation updated with configuration parameter name, format, and examples
<!-- AC:END -->
