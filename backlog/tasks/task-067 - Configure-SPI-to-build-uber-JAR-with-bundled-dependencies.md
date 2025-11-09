---
id: task-067
title: Configure SPI to build uber JAR with bundled dependencies
status: In Progress
assignee:
  - '@assistant'
created_date: '2025-11-09 12:32'
updated_date: '2025-11-09 12:33'
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

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Locate and examine the SPI pom.xml file
2. Add maven-shade-plugin configuration to create uber JAR with all dependencies
3. Configure plugin to include Kafka client and transitive dependencies
4. Build the SPI and verify JAR contents
5. Deploy uber JAR to Keycloak providers directory
6. Start Keycloak and verify startup succeeds
7. Check Keycloak logs to confirm SPI loaded correctly
8. Run E2E tests from task-64 to validate functionality
<!-- SECTION:PLAN:END -->
