---
id: task-004
title: Configure Kafka AdminClient connectivity
status: To Do
assignee: []
created_date: '2025-11-04 14:34'
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
- [ ] #1 AdminClient bean is configured with all required security properties
- [ ] #2 Configuration reads KAFKA_BOOTSTRAP_SERVERS, KAFKA_SECURITY_PROTOCOL, KAFKA_SASL_MECHANISM, and KAFKA_SASL_JAAS from environment
- [ ] #3 Connection to Kafka cluster is validated on startup
- [ ] #4 AdminClient can successfully execute basic operations (e.g., listTopics)
- [ ] #5 Connection errors are properly logged with context
- [ ] #6 AdminClient is included in health check status
<!-- AC:END -->
