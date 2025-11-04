---
id: task-007
title: Implement configuration management with environment variables
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-04 14:34'
updated_date: '2025-11-04 17:35'
labels:
  - backend
  - configuration
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Create a centralized configuration system that reads all required environment variables for Kafka, Keycloak, SQLite, retention, and server settings. Implement validation and provide clear error messages for missing or invalid configuration.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Configuration class/properties for all environment variables in technical analysis section 8
- [ ] #2 All configurations have sensible defaults where appropriate
- [ ] #3 Missing required configurations fail fast at startup with clear error messages
- [ ] #4 Configuration values are validated (e.g., URLs are valid, integers are positive)
- [ ] #5 Sensitive values (passwords, secrets) are not logged
- [ ] #6 Configuration documentation is available in application.properties or README
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Analyze section 8 environment variables from technical analysis
2. Create configuration classes using Quarkus @ConfigProperties for each group:
   - KafkaConfig (bootstrap servers, security, SASL)
   - KeycloakConfig (base URL, realm, client credentials, webhook secret)
   - ReconcileConfig (interval, page size)
   - RetentionConfig (max bytes, max age days, purge interval)
   - ServerConfig (port, basic auth)
3. Add validation annotations (@NotNull, @Min, @Pattern for URLs)
4. Implement custom validators for complex validation (e.g., JAAS config format)
5. Create a ConfigValidator bean to fail-fast at startup
6. Implement SensitiveDataFilter to mask passwords/secrets in logs
7. Document all configurations with descriptions and defaults in application.properties
8. Write unit tests for validation logic
9. Test fail-fast behavior with missing/invalid configurations
<!-- SECTION:PLAN:END -->
