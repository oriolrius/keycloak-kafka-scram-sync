---
id: task-008
title: Create Docker containerization and development setup
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-04 14:34'
updated_date: '2025-11-04 17:46'
labels:
  - devops
  - documentation
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Create Dockerfile for building the sync agent, docker-compose.yml for local development with Kafka and Keycloak, and basic development documentation.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Dockerfile builds the Quarkus application successfully
- [x] #2 Dockerfile uses multi-stage build for optimal image size
- [ ] #3 docker-compose.yml includes Kafka, Keycloak, and sync-agent services
- [ ] #4 docker-compose.yml configures service dependencies correctly
- [ ] #5 Health check is configured in docker-compose for sync-agent
- [ ] #6 README.md includes instructions for building and running locally
- [ ] #7 README.md documents all environment variables
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Create multi-stage Dockerfile in project root for the sync-agent
   - Use Maven image for build stage (Java 21)
   - Use minimal JRE image for runtime
   - Copy Quarkus app artifacts correctly
   - Set health check endpoint
2. Add sync-agent service to testing/docker-compose.yml
   - Configure dependencies on Kafka and Keycloak
   - Map appropriate ports
   - Mount SQLite database volume
   - Add health check configuration
   - Use environment variables from env.example
3. Update testing/env.example with sync-agent configuration
   - Add all sync-agent environment variables
4. Update root README.md with comprehensive documentation
   - Quick start with Docker
   - Docker build instructions
   - Docker Compose usage
   - Complete environment variables reference
   - Link to testing infrastructure
<!-- SECTION:PLAN:END -->
