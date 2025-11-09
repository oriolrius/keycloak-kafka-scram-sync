---
id: task-067
title: Configure SPI to build uber JAR with bundled dependencies
status: To Do
assignee: []
created_date: '2025-11-09 12:32'
labels:
  - spi
  - build
  - packaging
dependencies:
  - task-064
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The Keycloak Password Sync SPI currently builds a slim JAR without bundling its Kafka client dependencies, causing Keycloak to fail at startup with ClassNotFoundException for org.apache.kafka.clients.admin.AdminClient. Configure the SPI pom.xml to use maven-shade-plugin or maven-assembly-plugin to create an uber JAR that includes all runtime dependencies.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 SPI pom.xml configured with maven-shade-plugin or maven-assembly-plugin
- [ ] #2 Uber JAR includes Kafka client dependencies and transitive dependencies
- [ ] #3 Keycloak starts successfully with the new SPI JAR loaded
- [ ] #4 SPI logs confirm it loaded correctly in Keycloak
- [ ] #5 E2E tests from task-64 pass with the deployed SPI
<!-- AC:END -->
