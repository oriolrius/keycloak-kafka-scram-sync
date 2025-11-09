---
id: task-065
title: Remove sync-agent components not needed for direct SPI
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-09 11:18'
updated_date: '2025-11-09 13:06'
labels:
  - cleanup
  - sync-agent
dependencies:
  - task-064
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
In the feature branch, remove or disable sync-agent components that are obsolete with direct Kafka SPI: webhook endpoint (PasswordWebhookResource), password cache, reconciliation triggers for password sync. Keep reconciliation as safety net for manual runs only. Be radical - remove everything that direct SPI makes unnecessary.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 PasswordWebhookResource removed or disabled
- [ ] #2 Password cache removed from ReconciliationService
- [ ] #3 Scheduled reconciliation kept as manual safety net only
- [ ] #4 Sync-agent compiles without webhook/cache code
- [ ] #5 Docker compose still starts sync-agent successfully
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Search for PasswordWebhookResource class
2. Search for password cache in ReconciliationService
3. Search for scheduled reconciliation triggers
4. Remove/disable PasswordWebhookResource (AC #1)
5. Remove password cache from ReconciliationService (AC #2)
6. Convert scheduled reconciliation to manual-only (AC #3)
7. Compile sync-agent to verify no errors (AC #4)
8. Test docker compose startup (AC #5)
9. Document all changes in implementation notes
<!-- SECTION:PLAN:END -->
