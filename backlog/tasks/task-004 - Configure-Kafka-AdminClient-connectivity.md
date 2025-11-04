---
id: task-004
title: Configure Kafka AdminClient connectivity
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-04 14:34'
updated_date: '2025-11-04 17:11'
labels:
  - backend
  - kafka
  - connectivity
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Set up Kafka AdminClient with SASL_SSL authentication using SCRAM-SHA-512. Implement configuration from environment variables and verify connectivity.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 AdminClient bean is configured with all required security properties
- [x] #2 Configuration reads KAFKA_BOOTSTRAP_SERVERS, KAFKA_SECURITY_PROTOCOL, KAFKA_SASL_MECHANISM, and KAFKA_SASL_JAAS from environment
- [x] #3 Connection to Kafka cluster is validated on startup
- [x] #4 AdminClient can successfully execute basic operations (e.g., listTopics)
- [x] #5 Connection errors are properly logged with context
- [x] #6 AdminClient is included in health check status
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Review testing infrastructure Kafka configuration (SSL on port 57005, PLAINTEXT on 9092)
2. Create KafkaConfig class to manage Kafka configuration properties
3. Create KafkaAdminClientProducer CDI bean that produces AdminClient
4. Read configuration from environment variables: KAFKA_BOOTSTRAP_SERVERS, KAFKA_SECURITY_PROTOCOL, KAFKA_SASL_MECHANISM, KAFKA_SASL_JAAS
5. Support multiple security protocols: PLAINTEXT, SSL, SASL_SSL
6. Add SSL truststore/keystore configuration for SSL/SASL_SSL modes
7. Create KafkaConnectivityValidator to check connection on startup
8. Update KafkaHealthCheck to use injected AdminClient bean
9. Add proper error logging with context
10. Test with PLAINTEXT mode (port 9092)
11. Test with SSL mode using testing infrastructure (port 57005)
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
# Implementation Summary

Configured Kafka AdminClient with comprehensive security support (PLAINTEXT, SSL, SASL_SSL) and environment variable configuration. Implemented startup connectivity validation and updated health check to use injected AdminClient bean.

## What Was Done

- **Created KafkaConfig interface** (src/main/java/com/miimetiq/keycloak/sync/kafka/KafkaConfig.java)
  - ConfigMapping interface for Kafka configuration
  - Supports environment variable overrides: KAFKA_BOOTSTRAP_SERVERS, KAFKA_SECURITY_PROTOCOL, KAFKA_SASL_MECHANISM, KAFKA_SASL_JAAS
  - SSL configuration: truststore/keystore locations and passwords
  - SASL configuration: mechanism and JAAS config
  - Configurable timeouts for requests and connections

- **Created KafkaAdminClientProducer** (src/main/java/com/miimetiq/keycloak/sync/kafka/KafkaAdminClientProducer.java)
  - CDI bean producer for AdminClient
  - Automatically configures security based on protocol (PLAINTEXT, SSL, SASL_SSL)
  - SSL endpoint identification disabled for dev/testing with self-signed certs
  - Comprehensive logging of configuration (masks passwords)
  - Proper disposal with @Disposes method

- **Created KafkaConnectivityValidator** (src/main/java/com/miimetiq/keycloak/sync/kafka/KafkaConnectivityValidator.java)
  - Validates Kafka connectivity on application startup
  - Lists topics as connectivity test
  - Logs success/failure with detailed context
  - Application continues even if Kafka is unavailable (warns only)

- **Updated KafkaHealthCheck** (src/main/java/com/miimetiq/keycloak/sync/health/KafkaHealthCheck.java)
  - Now injects AdminClient bean instead of creating its own
  - Uses KafkaConfig for configuration details
  - Health response includes security protocol information
  - Part of /readyz endpoint checks

- **Updated application.properties** (src/main/resources/application.properties:16-38)
  - PLAINTEXT configuration for local development (port 9092)
  - Documented SSL configuration for testing infrastructure (port 57005)
  - Documented SASL configuration for SCRAM-SHA-512 auth
  - Disabled Kafka DevServices to use custom configuration
  - All properties support environment variable overrides

## Testing Results

### Startup Validation:
- Application starts successfully even when Kafka is unavailable
- Logs detailed error context when connection fails
- Logs success with topic count when connection succeeds

### Health Check:
- GET /readyz shows Kafka status with bootstrap servers and security protocol
- Returns DOWN when Kafka unavailable with error message
- Returns UP when Kafka is accessible

### Example Health Response:
```json
{
  "name": "kafka",
  "status": "DOWN",
  "data": {
    "bootstrap.servers": "localhost:9092",
    "security.protocol": "PLAINTEXT",
    "error": "Connection refused"
  }
}
```

## Configuration Examples

### PLAINTEXT (default):
```properties
kafka.bootstrap-servers=localhost:9092
kafka.security-protocol=PLAINTEXT
```

### SSL (testing infrastructure):
```properties
kafka.bootstrap-servers=localhost:57005
kafka.security-protocol=SSL
kafka.ssl-truststore-location=testing/certs/kafka.truststore.jks
kafka.ssl-truststore-password=changeit
```

### SASL_SSL (production):
```properties
kafka.bootstrap-servers=kafka.example:9093
kafka.security-protocol=SASL_SSL
kafka.sasl-mechanism=SCRAM-SHA-512
kafka.sasl-jaas=org.apache.kafka.common.security.scram.ScramLoginModule required username="user" password="pass";
kafka.ssl-truststore-location=/path/to/truststore.jks
kafka.ssl-truststore-password=secret
```

## Notes

- All configuration can be overridden via environment variables
- AdminClient is application-scoped singleton
- Connection errors are logged but don't prevent application startup
- Health check uses injected AdminClient for consistency
- SSL endpoint identification disabled for dev/testing with self-signed certificates
<!-- SECTION:NOTES:END -->
