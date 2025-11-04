---
id: task-014
title: Implement Kafka SCRAM credentials manager
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-04 18:33'
updated_date: '2025-11-04 19:10'
labels:
  - backend
  - kafka
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Create a service that manages SCRAM credentials in Kafka using the AdminClient API. Supports listing, describing, upserting, and deleting SCRAM credentials.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 KafkaScramManager service created with AdminClient integration
- [ ] #2 describeUserScramCredentials method returns map of principal to mechanisms
- [ ] #3 alterUserScramCredentials method supports both upsert and delete operations
- [ ] #4 Handles UserScramCredentialUpsertion for creating/updating credentials
- [ ] #5 Handles UserScramCredentialDeletion for removing credentials
- [ ] #6 Supports batch operations (multiple principals in single API call)
- [ ] #7 Returns AlterUserScramCredentialsResult with per-principal futures
- [ ] #8 Implements proper error handling for Kafka API exceptions
- [ ] #9 Logs all operations with principal, mechanism, and operation type
- [ ] #10 Unit tests with mocked AdminClient
- [ ] #11 Integration test validates operations against real Kafka (Testcontainers)
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Review existing Kafka AdminClient setup and ScramCredential domain model
2. Create KafkaScramManager service with @ApplicationScoped in kafka package
3. Inject AdminClient from KafkaAdminClientProducer
4. Implement describeUserScramCredentials() to fetch existing credentials
5. Implement alterUserScramCredentials() supporting both upsert and delete operations
6. Add batch operation support (List<UserScramCredentialAlteration>)
7. Implement comprehensive error handling for Kafka API exceptions
8. Add detailed logging for all operations
9. Create unit tests with mocked AdminClient
10. Test and verify all acceptance criteria
<!-- SECTION:PLAN:END -->
