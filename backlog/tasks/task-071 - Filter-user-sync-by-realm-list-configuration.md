---
id: task-071
title: Filter user sync by realm list configuration
status: Done
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

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Implementation Summary

Added realm-based filtering feature to allow selective synchronization of user passwords from specific Keycloak realms to Kafka. This provides better control in multi-realm deployments and improves efficiency by avoiding unnecessary synchronization.

## Changes Made

### Configuration Support
- Added support for three configuration methods with clear priority:
  1. Config.Scope (Keycloak standalone.xml/domain.xml)
  2. Java system property: `password.sync.realms`
  3. Environment variable: `PASSWORD_SYNC_REALMS`

- Configuration accepts comma-separated realm names (e.g., "master,production,staging")
- Whitespace is automatically trimmed from realm names
- Empty or missing configuration defaults to syncing all realms (backward compatible)

### Core Implementation
**Modified Files:**
- `PasswordSyncEventListenerFactory.java`: Added realm list parsing, validation, and static storage (lines 27-79)
- `PasswordSyncEventListener.java`: Added realm filtering logic before password sync (lines 32-33, 35-38, 82-146)

**Key Features:**
- Extracts realm name from AdminEvent using Keycloak session API
- Checks if realm is in allowed list before syncing to Kafka
- Clears password from ThreadLocal even when filtering (prevents memory leaks)
- Logs clear messages when filtering occurs

### Testing
**New Test File:**
- `RealmFilteringTest.java`: 8 comprehensive tests covering:
  - Configuration parsing and validation
  - Priority of configuration sources (Config.Scope > System property > Environment variable)
  - Empty/whitespace handling
  - Single and multiple realm configurations
  - Whitespace trimming

**Test Results:**
- All 25 tests pass (17 existing + 8 new)
- No regressions in existing functionality
- Verified configuration priority works correctly

### Documentation
**Updated README.md** with new section "Realm Filtering Configuration (Optional)" including:
- Configuration table with new variables
- Examples for all three configuration methods (environment variable, system property, Config.Scope)
- Behavioral explanation (when enabled vs disabled)
- Example startup log messages

## Backward Compatibility

Fully backward compatible:
- When not configured, all realms are synced (existing behavior)
- No breaking changes to existing configurations
- Existing tests continue to pass

## Example Usage

```bash
# Docker/Kubernetes deployment
export PASSWORD_SYNC_REALMS=master,production,staging

# Results in log:
# INFO: Realm filtering ENABLED. Password sync will be restricted to realms: master, production, staging
```

Users in non-listed realms will have their password changes applied in Keycloak but **not** synced to Kafka, with a clear log message:
```
INFO: Skipping password sync for user in realm 'test-realm' (not in allowed list: master, production, staging)
```
<!-- SECTION:NOTES:END -->
