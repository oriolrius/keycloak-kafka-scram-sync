---
id: task-063
title: Remove webhook and cache code from SPI branch
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-09 11:18'
updated_date: '2025-11-09 12:14'
labels:
  - cleanup
  - spi
dependencies:
  - task-062
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Remove all webhook-related code from the feature branch: remove HTTP POST to sync-agent webhook, remove ThreadLocal password sharing, remove webhook endpoint dependencies. Keep only direct Kafka sync logic in the SPI. Be radical but use common sense - remove everything not needed for direct Kafka approach.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Webhook HTTP client code removed from SPI
- [ ] #2 ThreadLocal password storage removed
- [ ] #3 PasswordWebhookResource reference removed
- [ ] #4 SPI only contains direct Kafka sync logic
- [ ] #5 SPI compiles successfully without webhook dependencies
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Examine current SPI implementation to identify all webhook-related code
2. Remove webhook HTTP client code from PasswordUpdateEventListener
3. Remove ThreadLocal password storage mechanism
4. Remove PasswordWebhookResource references and dependencies
5. Clean up any webhook-related imports and dependencies
6. Verify SPI compiles successfully
7. Test that direct Kafka sync still works correctly
<!-- SECTION:PLAN:END -->
