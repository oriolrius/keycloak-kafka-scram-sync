---
id: task-060
title: Add Kafka AdminClient to Keycloak SPI with ENV configuration
status: Done
assignee:
  - '@claude'
created_date: '2025-11-09 11:17'
updated_date: '2025-11-09 11:50'
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
- [x] #2 KafkaAdminClientFactory class created that reads ENV variables
- [x] #3 AdminClient connection successfully established to Kafka broker
- [x] #4 Configuration supports PLAINTEXT and SSL protocols via ENV
- [x] #5 Connection is properly closed on SPI shutdown
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

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Added Kafka AdminClient support to the Keycloak SPI with environment-based configuration.

**Changes Made:**
- Added kafka-clients 3.9.0 dependency to keycloak-password-sync-spi/pom.xml
- Created KafkaAdminClientFactory singleton factory class that:
  - Reads configuration from environment variables (KAFKA_BOOTSTRAP_SERVERS, KAFKA_SECURITY_PROTOCOL, etc.)
  - Supports PLAINTEXT and SSL security protocols
  - Supports SASL authentication mechanisms
  - Manages AdminClient lifecycle (create/close)
- Integrated factory into PasswordSyncEventListenerFactory:
  - AdminClient initialized in init() method
  - AdminClient closed in close() method
- Added JUnit 5 test infrastructure and test class KafkaAdminClientFactoryTest
- Build successful, AdminClient connection verified through test logs

**Architecture:**
- Singleton pattern for AdminClient instance
- Thread-safe initialization using synchronized methods
- Follows same configuration pattern as sync-agent KafkaAdminClientProducer
- Environment variables provide flexibility for different deployment scenarios (dev, staging, production)

**Environment Variables Supported:**
- KAFKA_BOOTSTRAP_SERVERS (default: localhost:9092)
- KAFKA_SECURITY_PROTOCOL (default: PLAINTEXT)
- KAFKA_SASL_MECHANISM, KAFKA_SASL_JAAS_CONFIG (for SASL)
- KAFKA_SSL_TRUSTSTORE_LOCATION, KAFKA_SSL_TRUSTSTORE_PASSWORD (for SSL)
- KAFKA_SSL_KEYSTORE_LOCATION, KAFKA_SSL_KEYSTORE_PASSWORD, KAFKA_SSL_KEY_PASSWORD (for SSL)
- KAFKA_REQUEST_TIMEOUT_MS (default: 30000)
- KAFKA_DEFAULT_API_TIMEOUT_MS (default: 60000)

The SPI is now ready to use Kafka AdminClient for SCRAM user management operations.
<!-- SECTION:NOTES:END -->
