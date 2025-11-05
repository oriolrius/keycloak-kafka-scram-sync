---
id: task-039
title: Implement HMAC signature verification for webhook events
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-05 10:16'
updated_date: '2025-11-05 10:22'
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
