---
id: task-039
title: Implement HMAC signature verification for webhook events
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-05 10:16'
updated_date: '2025-11-05 10:23'
labels:
  - sprint-4
  - webhook
  - security
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add security validation for incoming webhook events by verifying HMAC signatures. Keycloak sends a signature header, and the service must validate it using the configured secret (KC_WEBHOOK_HMAC_SECRET) to prevent unauthorized event injection.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 HMAC-SHA256 signature verification implemented
- [ ] #2 Configuration supports KC_WEBHOOK_HMAC_SECRET environment variable
- [ ] #3 Invalid signatures return 401 Unauthorized
- [ ] #4 Missing signature header returns 401 Unauthorized
- [ ] #5 Valid signatures allow event processing to proceed
- [ ] #6 Unit tests cover signature validation logic
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Review existing KeycloakConfig - webhookHmacSecret() already exists
2. Create WebhookSignatureValidator service for HMAC-SHA256 verification
3. Implement signature validation logic with proper error handling
4. Update KeycloakWebhookResource to verify X-Keycloak-Signature header
5. Return 401 Unauthorized for missing or invalid signatures
6. Create comprehensive unit tests for signature validation scenarios
7. Create integration tests with valid/invalid signatures
8. Run all tests and verify acceptance criteria
<!-- SECTION:PLAN:END -->
