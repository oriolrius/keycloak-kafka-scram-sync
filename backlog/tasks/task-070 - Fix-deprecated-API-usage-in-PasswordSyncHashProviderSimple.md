---
id: task-070
title: Fix deprecated API usage in PasswordSyncHashProviderSimple
status: Done
assignee:
  - '@claude'
created_date: '2025-11-10 16:07'
updated_date: '2025-11-10 17:03'
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
- [x] #1 Identify which Keycloak API method is deprecated by compiling with -Xlint:deprecation flag
- [x] #2 Update PasswordSyncHashProviderSimple.java to use the recommended replacement API
- [x] #3 Verify compilation completes without deprecation warnings
- [x] #4 Run existing tests to ensure functionality remains unchanged
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Identify the deprecated API by reviewing Keycloak documentation and web search
2. Analyze the code to understand which method is deprecated
3. Remove the deprecated `encode(String, int)` method override
4. Compile to verify no warnings
5. Run tests to ensure no regression
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Fixed the deprecated API usage by removing the override of the deprecated `encode(String, int)` method (lines 63-70).

The issue was that `PasswordSyncHashProviderSimple` was overriding the deprecated `encode(String rawPassword, int iterations)` method from the `PasswordHashProvider` interface. According to Keycloak 26.0 documentation, this method is deprecated and will be removed in future versions. The recommended approach is to only implement `encodedCredential(String, int)`, which was already present in the class.

**Changes made:**
- Removed the deprecated `encode(String, int)` method override from PasswordSyncHashProviderSimple.java

**Verification:**
- Compilation completes without deprecation warnings
- All 17 tests pass (ScramCredentialGeneratorTest: 14 tests, KafkaAdminClientFactoryTest: 3 tests)
- No functional changes - the class already implements the recommended `encodedCredential` method
- The private `encode(String, int, byte[])` helper method remains unchanged
<!-- SECTION:NOTES:END -->
