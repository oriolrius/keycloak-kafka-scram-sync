---
id: task-061
title: Copy SCRAM credential generator to SPI module
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-09 11:17'
updated_date: '2025-11-09 11:59'
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
