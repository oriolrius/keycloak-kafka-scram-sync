---
id: task-013
title: Implement Keycloak user fetcher service
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-04 18:33'
updated_date: '2025-11-04 19:05'
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

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Create KeycloakUserInfo model class in domain package with required fields (id, username, email, enabled, createdTimestamp)
2. Create KeycloakUserFetcher service with @ApplicationScoped in keycloak package
3. Inject Keycloak client and ReconcileConfig (for pageSize)
4. Implement fetchAllUsers() with pagination logic using Keycloak Admin API
5. Add retry logic with exponential backoff (3 retries using Failsafe library or manual implementation)
6. Add filtering logic for service accounts (check username patterns)
7. Add comprehensive logging with SLF4J
8. Create unit tests with mocked Keycloak Admin client
9. Create integration test using Testcontainers for Keycloak
10. Test and verify all acceptance criteria
<!-- SECTION:PLAN:END -->
