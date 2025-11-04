---
id: task-012
title: Implement SCRAM credential generator
status: Done
assignee:
  - '@claude'
created_date: '2025-11-04 18:33'
updated_date: '2025-11-04 19:00'
labels:
  - backend
  - crypto
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Create a service that generates SCRAM-SHA-256 and SCRAM-SHA-512 verifiers (storedKey, serverKey, salt, iterations) from plaintext passwords. This is required for creating Kafka SCRAM credentials from Keycloak user passwords.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 ScramCredentialGenerator service created with methods for both SHA-256 and SHA-512
- [x] #2 Implementation follows RFC 5802 SCRAM specification
- [x] #3 Generates salt using SecureRandom (32 bytes)
- [x] #4 Computes SaltedPassword using PBKDF2 with configurable iterations (default 4096)
- [x] #5 Computes ClientKey = HMAC(SaltedPassword, "Client Key")
- [x] #6 Computes StoredKey = H(ClientKey)
- [x] #7 Computes ServerKey = HMAC(SaltedPassword, "Server Key")
- [x] #8 Returns ScramCredential object with storedKey, serverKey, salt, iterations in Base64
- [x] #9 Unit tests validate against known test vectors
- [x] #10 Unit tests verify both SHA-256 and SHA-512 mechanisms
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Create ScramCredential domain object to hold storedKey, serverKey, salt, and iterations
2. Create ScramCredentialGenerator service with RFC 5802 implementation
3. Implement generateScramSha256() and generateScramSha512() methods
4. Implement helper methods for PBKDF2, HMAC, and SHA operations
5. Write unit tests with RFC 5802 test vectors
6. Verify both SHA-256 and SHA-512 mechanisms work correctly
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Implementation Summary

Successfully implemented SCRAM credential generator following RFC 5802 specification.

## Changes Made

**1. Created ScramCredential domain object** (`src/main/java/com/miimetiq/keycloak/sync/domain/ScramCredential.java`)
- Immutable value object holding storedKey, serverKey, salt, and iterations
- All values stored in Base64 format ready for Kafka
- Includes input validation and secure toString() that redacts sensitive keys

**2. Created ScramCredentialGenerator service** (`src/main/java/com/miimetiq/keycloak/sync/crypto/ScramCredentialGenerator.java`)
- ApplicationScoped CDI bean for dependency injection
- Implements RFC 5802 SCRAM algorithm correctly:
  - Step 1: Generate 32-byte random salt using SecureRandom
  - Step 2: Compute SaltedPassword using PBKDF2
  - Step 3: Compute ClientKey = HMAC(SaltedPassword, "Client Key")
  - Step 4: Compute StoredKey = H(ClientKey)
  - Step 5: Compute ServerKey = HMAC(SaltedPassword, "Server Key")
- Supports both SCRAM-SHA-256 and SCRAM-SHA-512 mechanisms
- Configurable iteration count (default 4096)
- Custom ScramGenerationException for error handling

**3. Comprehensive test coverage** (`src/test/java/com/miimetiq/keycloak/sync/crypto/ScramCredentialGeneratorTest.java`)
- 14 unit tests, all passing
- Tests include:
  - Basic functionality for both SHA-256 and SHA-512
  - Custom iteration counts
  - Input validation (null passwords, invalid iterations)
  - Different passwords produce different credentials
  - Same password produces different salts (randomization)
  - RFC 5802 algorithm verification with deterministic test vectors
  - Key length validation (32 bytes for SHA-256, 64 bytes for SHA-512)
  - Secure toString() verification

## Testing Results

All 14 tests pass successfully:
- ✅ SHA-256 credential generation with default iterations
- ✅ SHA-512 credential generation with default iterations
- ✅ Custom iteration counts
- ✅ Input validation
- ✅ Randomization verification
- ✅ RFC 5802 algorithm correctness
- ✅ Key length validation
- ✅ Security (key redaction in toString)

## Architecture Notes

- Created new `crypto` package for cryptographic operations
- Service is CDI-managed (@ApplicationScoped) for easy injection
- No external dependencies required (uses JDK crypto APIs)
- Thread-safe implementation (SecureRandom is thread-safe)

## Ready for Integration

The ScramCredentialGenerator is ready to be used by:
- Keycloak user fetcher service (task-013)
- Kafka SCRAM credentials manager (task-014)
- Reconciliation services that need to generate Kafka credentials from Keycloak passwords
<!-- SECTION:NOTES:END -->
