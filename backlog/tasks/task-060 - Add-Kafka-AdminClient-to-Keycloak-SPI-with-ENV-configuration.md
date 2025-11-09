---
id: task-060
title: Add Kafka AdminClient to Keycloak SPI with ENV configuration
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-09 11:17'
updated_date: '2025-11-09 11:45'
labels:
  - spi
  - kafka
  - infrastructure
dependencies:
  - task-059
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add Kafka AdminClient dependency to the Keycloak SPI module and implement connection management using environment variables for configuration (bootstrap servers, security protocol, SSL settings). Reuse existing Kafka connection code from sync-agent where possible.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Kafka client dependency added to keycloak-password-sync-spi/pom.xml
- [ ] #2 KafkaAdminClientFactory class created that reads ENV variables
- [ ] #3 AdminClient connection successfully established to Kafka broker
- [ ] #4 Configuration supports PLAINTEXT and SSL protocols via ENV
- [ ] #5 Connection is properly closed on SPI shutdown
<!-- AC:END -->
