---
id: task-062
title: Implement direct Kafka sync in PasswordHashProvider SPI
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-09 11:18'
updated_date: '2025-11-09 12:05'
labels:
  - spi
  - core-logic
dependencies:
  - task-061
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Modify the PasswordHashProviderSimple to sync passwords directly to Kafka on password change/creation. When password is set, immediately generate SCRAM credentials and upsert to Kafka via AdminClient. If Kafka connection fails, the Keycloak password change should fail with clear error message.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 On password update, SCRAM credentials are generated and sent to Kafka
- [ ] #2 Successful Kafka sync allows password change to complete
- [ ] #3 Kafka connection failure causes password change to fail with error message
- [ ] #4 Error message clearly indicates Kafka connectivity issue
- [ ] #5 Password change transaction is atomic (both succeed or both fail)
<!-- AC:END -->
