---
id: task-004
title: Configure Kafka AdminClient connectivity
status: In Progress
assignee:
  - '@claude'
created_date: '2025-11-04 14:34'
updated_date: '2025-11-04 17:10'
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
