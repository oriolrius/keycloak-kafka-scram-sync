---
id: task-014
title: Implement Kafka SCRAM credentials manager
status: To Do
assignee: []
created_date: '2025-11-04 18:33'
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
