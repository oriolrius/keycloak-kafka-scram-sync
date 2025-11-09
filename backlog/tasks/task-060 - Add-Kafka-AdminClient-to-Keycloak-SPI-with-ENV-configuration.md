---
id: task-060
title: Add Kafka AdminClient to Keycloak SPI with ENV configuration
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-09 11:17'
updated_date: '2025-11-09 11:46'
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
- [x] #1 Kafka client dependency added to keycloak-password-sync-spi/pom.xml
- [ ] #2 KafkaAdminClientFactory class created that reads ENV variables
- [ ] #3 AdminClient connection successfully established to Kafka broker
- [ ] #4 Configuration supports PLAINTEXT and SSL protocols via ENV
- [ ] #5 Connection is properly closed on SPI shutdown
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Examine existing SPI structure in keycloak-password-sync-spi/
2. Review sync-agent's Kafka configuration for reusable patterns
3. Add kafka-clients dependency to keycloak-password-sync-spi/pom.xml
4. Create KafkaAdminClientFactory class with ENV-based configuration
5. Implement connection lifecycle management (init/shutdown)
6. Add support for PLAINTEXT and SSL security protocols via ENV variables
7. Test connection to Kafka broker
8. Verify proper cleanup on shutdown
<!-- SECTION:PLAN:END -->
