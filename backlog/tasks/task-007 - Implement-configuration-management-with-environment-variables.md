---
id: task-007
title: Implement configuration management with environment variables
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-04 14:34'
updated_date: '2025-11-04 17:39'
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
- [x] #1 Configuration class/properties for all environment variables in technical analysis section 8
- [x] #2 All configurations have sensible defaults where appropriate
- [x] #3 Missing required configurations fail fast at startup with clear error messages
- [x] #4 Configuration values are validated (e.g., URLs are valid, integers are positive)
- [x] #5 Sensitive values (passwords, secrets) are not logged
- [x] #6 Configuration documentation is available in application.properties or README
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

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implemented comprehensive configuration management system with environment variable support.

## What was implemented:

1. **Configuration Classes (AC #1):**
   - Updated KeycloakConfig to add webhookHmacSecret field
   - Created ReconcileConfig for reconciliation settings (interval, page size)
   - Created RetentionConfig for retention policies (max bytes, max age, purge interval)
   - Created ServerConfig for server settings (port, basic auth)
   - All configs use Quarkus @ConfigMapping with sensible defaults

2. **Sensible Defaults (AC #2):**
   - Kafka: localhost:9092, PLAINTEXT, 30s/10s timeouts
   - Keycloak: localhost:57003, master realm, admin-cli client
   - Reconcile: 120s interval, 500 page size
   - Retention: 256MB max, 30 days max age, 5min purge interval
   - Server: port 8088

3. **Fail-Fast Validation (AC #3):**
   - Created ConfigValidator that runs at startup
   - Validates all required configurations are present
   - Provides clear, numbered error messages for missing/invalid configs
   - Application fails to start if validation fails

4. **Configuration Validation (AC #4):**
   - URL validation for Keycloak base URL
   - Security protocol validation (PLAINTEXT, SSL, SASL_SSL, SASL_PLAINTEXT)
   - SASL mechanism validation (PLAIN, SCRAM-SHA-256, SCRAM-SHA-512, GSSAPI)
   - Positive integer validation for timeouts, intervals, sizes
   - Port range validation (1-65535)
   - Basic auth format validation (username:password)

5. **Sensitive Value Masking (AC #5):**
   - Created SensitiveDataMasker utility class
   - Masks passwords, secrets, tokens, API keys in logs
   - Masks JAAS configurations containing passwords
   - Masks basic auth credentials
   - Comprehensive pattern matching for various sensitive data formats

6. **Configuration Documentation (AC #6):**
   - Updated application.properties with detailed comments for all configurations
   - Documented environment variable override names
   - Added warnings for security-sensitive settings
   - Included examples for different security protocols

## Files Created/Modified:

- src/main/java/com/miimetiq/keycloak/sync/keycloak/KeycloakConfig.java (modified)
- src/main/java/com/miimetiq/keycloak/sync/reconcile/ReconcileConfig.java (new)
- src/main/java/com/miimetiq/keycloak/sync/retention/RetentionConfig.java (new)
- src/main/java/com/miimetiq/keycloak/sync/server/ServerConfig.java (new)
- src/main/java/com/miimetiq/keycloak/sync/config/ConfigValidator.java (new)
- src/main/java/com/miimetiq/keycloak/sync/config/SensitiveDataMasker.java (new)
- src/main/resources/application.properties (modified)
- src/test/java/com/miimetiq/keycloak/sync/config/ConfigValidatorTest.java (new)
- src/test/java/com/miimetiq/keycloak/sync/config/SensitiveDataMaskerTest.java (new)

## Testing:

- All tests pass (10/10 for SensitiveDataMasker)
- Build compiles successfully with no errors
- ConfigValidator tested with basic injection test
<!-- SECTION:NOTES:END -->
