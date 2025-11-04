---
id: task-001
title: Initialize Quarkus project with required dependencies
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-04 14:33'
updated_date: '2025-11-04 16:34'
labels:
  - backend
  - setup
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Set up the base Quarkus project structure with all necessary dependencies for the sync agent including Kafka AdminClient, Keycloak client, SQLite JDBC, Flyway, Micrometer, and RESTEasy Reactive.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Project builds successfully with Maven/Gradle
- [x] #2 All Sprint 1 dependencies are included in pom.xml/build.gradle
- [x] #3 Basic application.properties configuration file exists
- [x] #4 Project structure follows Quarkus conventions
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Initialize Quarkus project using Maven archetype
2. Add Sprint 1 dependencies: Kafka AdminClient, Keycloak client, SQLite JDBC, Flyway, Micrometer, RESTEasy Reactive
3. Create application.properties with basic configuration structure
4. Verify project structure follows Quarkus conventions (src/main/java, src/main/resources, etc.)
5. Build project to ensure all dependencies resolve correctly
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
# Implementation Summary

Initialized Quarkus project with all required Sprint 1 dependencies and configured for the testing infrastructure.

## What Was Done

- **Installed Quarkus CLI** via JBang for proper project scaffolding
- **Created Quarkus project** using CLI with groupId com.miimetiq and artifactId keycloak-kafka-sync-agent
- **Added all Sprint 1 dependencies** to pom.xml:
  - RESTEasy Reactive (quarkus-rest + quarkus-rest-jackson)
  - Kafka Client (quarkus-kafka-client)
  - Keycloak Admin Client (keycloak-admin-client v26.0.7)
  - SQLite JDBC (quarkus-jdbc-sqlite v3.0.11)
  - Hibernate ORM (quarkus-hibernate-orm)
  - Flyway (quarkus-flyway)
  - Micrometer Prometheus (quarkus-micrometer-registry-prometheus)
  - SmallRye Health (quarkus-smallrye-health)
  - Config YAML (quarkus-config-yaml)
  - Arc CDI (quarkus-arc)
- **Created application.properties** with configuration aligned to testing infrastructure:
  - Kafka: localhost:9092 (PLAINTEXT for testing)
  - Keycloak: https://localhost:57003 (HTTPS-only)
  - SQLite datasource with Flyway migrations
  - Prometheus metrics enabled
  - Health checks enabled
- **Verified project structure** follows Quarkus conventions:
  - src/main/java/com/miimetiq/keycloak/sync
  - src/main/resources
  - src/test/java/com/miimetiq/keycloak/sync
  - src/test/resources

## Build Verification

Project builds successfully with `./mvnw clean compile`

## Notes

- Configuration is aligned with the testing infrastructure defined in testing/docker-compose.yml
- SSL/TLS endpoints are documented but commented out in application.properties for easier initial development
- Maven wrapper included for portability
<!-- SECTION:NOTES:END -->
