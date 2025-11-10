---
id: task-070
title: Fix deprecated API usage in PasswordSyncHashProviderSimple
status: To Do
assignee: []
created_date: '2025-11-10 16:07'
labels:
  - technical-debt
  - maintenance
  - keycloak
dependencies: []
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The PasswordSyncHashProviderSimple.java class uses or overrides a deprecated Keycloak API, which generates a compilation warning. While this currently has no functional impact, it should be updated to use the newer Keycloak API to ensure future compatibility and maintain code quality.

The warning appears during Maven compilation:
```
[INFO] /home/runner/work/.../PasswordSyncHashProviderSimple.java: 
  PasswordSyncHashProviderSimple.java uses or overrides a deprecated API.
[INFO] Recompile with -Xlint:deprecation for details.
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Identify which Keycloak API method is deprecated by compiling with -Xlint:deprecation flag
- [ ] #2 Update PasswordSyncHashProviderSimple.java to use the recommended replacement API
- [ ] #3 Verify compilation completes without deprecation warnings
- [ ] #4 Run existing tests to ensure functionality remains unchanged
<!-- AC:END -->
