---
id: task-013
title: Implement Keycloak user fetcher service
status: To Do
assignee: []
created_date: '2025-11-04 18:33'
labels:
  - backend
  - keycloak
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Create a service that fetches all users from Keycloak with pagination support. This service will be used by the reconciliation engine to get the source of truth for user identities.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 KeycloakUserFetcher service created with fetchAllUsers method
- [ ] #2 Supports pagination with configurable page size (default 500 from config)
- [ ] #3 Returns list of KeycloakUserInfo objects with (id, username, email, enabled, createdTimestamp)
- [ ] #4 Handles Keycloak API errors gracefully with appropriate exceptions
- [ ] #5 Implements retry logic for transient failures (3 retries with exponential backoff)
- [ ] #6 Filters out service accounts or technical users if configured
- [ ] #7 Logs fetch operations with realm and count information
- [ ] #8 Unit tests with mocked Keycloak Admin client
- [ ] #9 Integration test validates fetching from real Keycloak (Testcontainers)
<!-- AC:END -->
