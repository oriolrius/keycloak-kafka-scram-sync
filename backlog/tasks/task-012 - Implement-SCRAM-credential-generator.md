---
id: task-012
title: Implement SCRAM credential generator
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-04 18:33'
updated_date: '2025-11-04 18:56'
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
- [ ] #1 ScramCredentialGenerator service created with methods for both SHA-256 and SHA-512
- [ ] #2 Implementation follows RFC 5802 SCRAM specification
- [ ] #3 Generates salt using SecureRandom (32 bytes)
- [ ] #4 Computes SaltedPassword using PBKDF2 with configurable iterations (default 4096)
- [ ] #5 Computes ClientKey = HMAC(SaltedPassword, "Client Key")
- [ ] #6 Computes StoredKey = H(ClientKey)
- [ ] #7 Computes ServerKey = HMAC(SaltedPassword, "Server Key")
- [ ] #8 Returns ScramCredential object with storedKey, serverKey, salt, iterations in Base64
- [ ] #9 Unit tests validate against known test vectors
- [ ] #10 Unit tests verify both SHA-256 and SHA-512 mechanisms
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
