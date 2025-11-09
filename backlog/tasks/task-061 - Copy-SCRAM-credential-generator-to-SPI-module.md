---
id: task-061
title: Copy SCRAM credential generator to SPI module
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-09 11:17'
updated_date: '2025-11-09 12:00'
labels:
  - spi
  - crypto
dependencies:
  - task-060
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Copy the ScramCredentialGenerator class from sync-agent (src/main/java/com/miimetiq/keycloak/sync/crypto/) to the Keycloak SPI module. This provides RFC 5802 compliant SCRAM-SHA-256 credential generation directly in the SPI.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 ScramCredentialGenerator class copied to keycloak-password-sync-spi module
- [x] #2 Unit tests for SCRAM generation pass
- [x] #3 SCRAM credentials generate correctly with salt, iterations, and hashed password
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Locate and read ScramCredentialGenerator class in sync-agent module
2. Create crypto package in SPI module if it doesn't exist
3. Copy ScramCredentialGenerator to SPI module
4. Locate and copy unit tests for ScramCredentialGenerator
5. Run tests to verify SCRAM generation works correctly
6. Verify all acceptance criteria are met
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Successfully copied ScramCredentialGenerator and dependencies to the Keycloak SPI module.

**Files Created:**
- keycloak-password-sync-spi/src/main/java/com/miimetiq/keycloak/spi/crypto/ScramCredentialGenerator.java
- keycloak-password-sync-spi/src/main/java/com/miimetiq/keycloak/spi/domain/ScramCredential.java
- keycloak-password-sync-spi/src/main/java/com/miimetiq/keycloak/spi/domain/enums/ScramMechanism.java
- keycloak-password-sync-spi/src/test/java/com/miimetiq/keycloak/spi/crypto/ScramCredentialGeneratorTest.java

**Changes Made:**
- Copied ScramCredentialGenerator from sync-agent to SPI module with updated package names
- Copied supporting domain classes (ScramCredential, ScramMechanism) to SPI module
- Removed @ApplicationScoped annotation since SPI is lightweight and doesn't use CDI
- Copied comprehensive test suite (14 tests) including RFC 5802 compliance tests
- All tests pass successfully (Tests run: 14, Failures: 0, Errors: 0, Skipped: 0)

**Architecture:**
- RFC 5802 compliant SCRAM-SHA-256 and SCRAM-SHA-512 credential generation
- Supports custom iteration counts (default: 4096)
- Generates cryptographically secure random salts (32 bytes)
- Produces Base64-encoded credentials ready for Kafka storage
- Includes proper key derivation using PBKDF2 and HMAC

The SPI module now has self-contained SCRAM credential generation capability, ready for integration with Kafka user management.
<!-- SECTION:NOTES:END -->
